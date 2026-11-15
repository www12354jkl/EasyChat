package com.faust.easychat.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GatewayAuthFilterTest {

    private static final String SECRET = "12345678901234567890123456789012";
    private static final String OTHER_SECRET = "abcdefghijklmnopqrstuvwxyz123456";

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> values;

    private GatewayAuthFilter filter;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        filter = new GatewayAuthFilter(redisTemplate, SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void login_isPublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());

        assertTrue(invokeFilter(exchange));
    }

    @Test
    void publicEndpoints_allowConfiguredPaths_andRejectPrefixBypassOrTraversal() {
        List<String> allowedPaths = List.of(
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/token/refresh",
                "/api/auth/send-verify-code",
                "/api/auth/forget-password",
                "/swagger-ui.html",
                "/swagger-ui/index.html",
                "/v3/api-docs/user",
                "/webjars/swagger-ui/swagger-ui.css");
        for (String path : allowedPaths) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
            assertTrue(invokeFilter(exchange), "Expected public path to pass: " + path);
        }

        List<String> protectedPaths = List.of(
                "/api/auth/logout",
                "/api/auth/login-evil",
                "/api/auth/login/extra",
                "/api/auth/login/../logout",
                "/swagger-ui-evil",
                "/swagger-ui/../api/auth/logout",
                "/v3/api-docs-evil",
                "/ws-admin/status");
        for (String path : protectedPaths) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
            assertFalse(invokeFilter(exchange), "Expected protected path to require auth: " + path);
            assertRejectedWithCode401(exchange, 200);
        }
    }

    @Test
    void optionsPreflight_passesWithoutAuth() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/user/profile").build());

        assertTrue(invokeFilter(exchange));
    }

    @Test
    void missingToken_isRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chat/messages").build());

        assertFalse(invokeFilter(exchange));
        assertRejectedWithCode401(exchange, 200);
    }

    @Test
    void validAccessToken_passes() {
        stubNotBlacklistedAndNoRevocation();
        String token = buildAccessToken("user-1", new Date());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "Bearer " + token).build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        assertTrue(invokeFilter(exchange, forwarded));
        assertNotNull(forwarded.get().getRequest().getHeaders().getFirst("X-Request-Id"));
    }

    @Test
    void blacklistedAccessToken_isRejectedWithoutCallingChain() {
        String token = buildAccessToken("user-1", new Date());
        when(redisTemplate.hasKey("token:blacklist:" + token)).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "Bearer " + token).build());

        assertFalse(invokeFilter(exchange));
        assertRejectedWithCode401(exchange, 200);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void logout_isNotPublic_missingOrBlacklistedTokenRejected() {
        MockServerWebExchange missingAuthExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/logout").build());
        assertFalse(invokeFilter(missingAuthExchange));
        assertRejectedWithCode401(missingAuthExchange, 200);

        String token = buildAccessToken("user-1", new Date());
        when(redisTemplate.hasKey("token:blacklist:" + token)).thenReturn(Mono.just(true));
        MockServerWebExchange blacklistedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/logout").header("Authorization", "Bearer " + token).build());
        assertFalse(invokeFilter(blacklistedExchange));
        assertRejectedWithCode401(blacklistedExchange, 200);
    }

    @Test
    void logout_withValidAccessToken_passes() {
        stubNotBlacklistedAndNoRevocation();
        String token = buildAccessToken("user-1", new Date());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/logout").header("Authorization", "Bearer " + token).build());

        assertTrue(invokeFilter(exchange));
    }

    @Test
    void refreshTokenAsBearer_isRejected() {
        when(redisTemplate.hasKey(startsWith("token:blacklist:"))).thenReturn(Mono.just(false));
        String refreshToken = Jwts.builder()
                .subject("user-1")
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "Bearer " + refreshToken)
                        .build());

        assertFalse(invokeFilter(exchange));
        String body = assertRejectedWithCode401(exchange, 200);
        assertTrue(body.contains("无效token"));
    }

    @Test
    void tokenRevokedAtOrBeforeCutoff_isRejected_andTokenAfterCutoffPasses() {
        long cutoffMs = 1_700_000_000_000L;
        when(redisTemplate.hasKey(startsWith("token:blacklist:"))).thenReturn(Mono.just(false));
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("token:revoke-before:user-1")).thenReturn(Mono.just(String.valueOf(cutoffMs)));

        String beforeToken = buildAccessToken("user-1", new Date(cutoffMs - 1_000L));
        MockServerWebExchange beforeExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "Bearer " + beforeToken)
                        .build());
        assertFalse(invokeFilter(beforeExchange));
        assertRejectedWithCode401(beforeExchange, 200);

        String atCutoffToken = buildAccessToken("user-1", new Date(cutoffMs));
        MockServerWebExchange atCutoffExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "Bearer " + atCutoffToken)
                        .build());
        assertFalse(invokeFilter(atCutoffExchange));
        assertRejectedWithCode401(atCutoffExchange, 200);

        String noIssuedAtToken = Jwts.builder()
                .subject("user-1")
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        MockServerWebExchange noIssuedAtExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "Bearer " + noIssuedAtToken)
                        .build());
        assertFalse(invokeFilter(noIssuedAtExchange));
        assertRejectedWithCode401(noIssuedAtExchange, 200);

        String afterToken = buildAccessToken("user-1", new Date(cutoffMs + 1_000L));
        MockServerWebExchange afterExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "Bearer " + afterToken)
                        .build());
        assertTrue(invokeFilter(afterExchange));
    }

    @Test
    void redisDataAccessException_returns503() {
        String token = buildAccessToken("user-1", new Date());

        when(redisTemplate.hasKey("token:blacklist:" + token))
                .thenReturn(Mono.error(new RedisConnectionFailureException("redis down")));
        MockServerWebExchange blacklistFailureExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "Bearer " + token).build());
        assertFalse(invokeFilter(blacklistFailureExchange));
        assertServiceUnavailable(blacklistFailureExchange);

        when(redisTemplate.hasKey("token:blacklist:" + token)).thenReturn(Mono.just(false));
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("token:revoke-before:user-1"))
                .thenReturn(Mono.error(new QueryTimeoutException("redis timeout")));
        MockServerWebExchange revokeFailureExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "Bearer " + token).build());
        assertFalse(invokeFilter(revokeFailureExchange));
        assertServiceUnavailable(revokeFailureExchange);

        when(redisTemplate.hasKey("ws:ticket:abc"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        MockServerWebExchange wsFailureExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws?ticket=abc").header("Upgrade", "websocket").build());
        assertFalse(invokeFilter(wsFailureExchange));
        assertServiceUnavailable(wsFailureExchange);
    }

    @Test
    void expiredOrInvalidJwt_returns401() {
        when(redisTemplate.hasKey(startsWith("token:blacklist:"))).thenReturn(Mono.just(false));

        String expiredToken = Jwts.builder()
                .subject("user-1")
                .claim("type", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();
        MockServerWebExchange expiredExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "Bearer " + expiredToken)
                        .build());
        assertFalse(invokeFilter(expiredExchange));
        assertRejectedWithCode401(expiredExchange, 200);

        SecretKey wrongKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String wrongSigToken = Jwts.builder()
                .subject("user-1")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(wrongKey)
                .compact();
        MockServerWebExchange wrongSigExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "Bearer " + wrongSigToken)
                        .build());
        assertFalse(invokeFilter(wrongSigExchange));
        assertRejectedWithCode401(wrongSigExchange, 200);

        MockServerWebExchange malformedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "Bearer not-a-jwt")
                        .build());
        assertFalse(invokeFilter(malformedExchange));
        assertRejectedWithCode401(malformedExchange, 200);
    }

    @Test
    void bearerHeaderExtraction_handlesWhitespaceAndCaseAndRejectsMalformed() {
        stubNotBlacklistedAndNoRevocation();
        String token = buildAccessToken("user-1", new Date());
        MockServerWebExchange messyValid = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "  bearer   " + token + "  ")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        assertTrue(invokeFilter(messyValid, forwarded));
        assertEquals("Bearer " + token,
                forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

        List<MockServerHttpRequest> malformedRequests = List.of(
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "Bearer   ").build(),
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "BearerToken").build(),
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "Basic dXNlcjpwYXNz").build(),
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "Bearer part1 part2").build(),
                MockServerHttpRequest.get("/api/user/profile")
                        .header("Authorization", "Bearer " + token, "Bearer extra")
                        .build());
        for (MockServerHttpRequest request : malformedRequests) {
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            assertFalse(invokeFilter(exchange));
            assertRejectedWithCode401(exchange, 200);
        }
    }

    @Test
    void wsTicket_passesWhenPresent_andRejectsMissingOrInvalid() {
        when(redisTemplate.hasKey("ws:ticket:abc")).thenReturn(Mono.just(true));
        MockServerWebExchange validWs = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws?ticket=abc")
                        .header("Upgrade", "websocket")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        assertTrue(invokeFilter(validWs, forwarded));
        assertEquals("abc", forwarded.get().getRequest().getHeaders().getFirst("X-Ws-Ticket"));

        when(redisTemplate.hasKey("ws:ticket:bad")).thenReturn(Mono.just(false));
        MockServerWebExchange invalidWs = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws?ticket=bad")
                        .header("Upgrade", "websocket")
                        .build());
        assertFalse(invokeFilter(invalidWs));
        assertRejectedWithCode401(invalidWs, 401);

        MockServerWebExchange missingTicketWs = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws")
                        .header("Upgrade", "websocket")
                        .build());
        assertFalse(invokeFilter(missingTicketWs));
        assertRejectedWithCode401(missingTicketWs, 401);
    }

    @Test
    void downstreamError_propagatesWithoutBeingMasked() {
        stubNotBlacklistedAndNoRevocation();
        String token = buildAccessToken("user-1", new Date());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "Bearer " + token).build());

        assertThrows(IllegalStateException.class, () -> filter.filter(exchange,
                ex -> Mono.error(new IllegalStateException("downstream failed"))).block());
    }

    @Test
    void springBoot4AutoConfiguration_wiresReactiveStringRedisTemplateAndGatewayAuthFilter() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataRedisAutoConfiguration.class,
                        DataRedisReactiveAutoConfiguration.class))
                .withBean(GatewayAuthFilter.class)
                .withPropertyValues("jwt.secret=" + SECRET)
                .run(context -> {
                    assertNotNull(context.getBean(ReactiveStringRedisTemplate.class));
                    assertNotNull(context.getBean(GatewayAuthFilter.class));
                });
    }

    private void stubNotBlacklistedAndNoRevocation() {
        when(redisTemplate.hasKey(startsWith("token:blacklist:"))).thenReturn(Mono.just(false));
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(Mono.empty());
    }

    private String buildAccessToken(String userId, Date issuedAt) {
        return Jwts.builder()
                .subject(userId)
                .claim("type", "access")
                .issuedAt(issuedAt)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private boolean invokeFilter(MockServerWebExchange exchange) {
        return invokeFilter(exchange, new AtomicReference<>());
    }

    private boolean invokeFilter(MockServerWebExchange exchange, AtomicReference<ServerWebExchange> captured) {
        AtomicBoolean passed = new AtomicBoolean();
        filter.filter(exchange, ex -> {
            passed.set(true);
            captured.set(ex);
            return Mono.empty();
        }).block();
        return passed.get();
    }

    private static String assertRejectedWithCode401(MockServerWebExchange exchange, int expectedHttpStatus) {
        assertEquals(expectedHttpStatus, exchange.getResponse().getStatusCode().value());
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        assertTrue(body.contains("\"code\":401"), "Expected code:401 in body but got: " + body);
        return body;
    }

    private static void assertServiceUnavailable(MockServerWebExchange exchange) {
        assertEquals(503, exchange.getResponse().getStatusCode().value());
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        assertTrue(body.contains("\"code\":503"), "Expected code:503 in body but got: " + body);
    }
}
