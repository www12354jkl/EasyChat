package com.faust.easychat.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GatewayAuthFilterTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;

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
        AtomicBoolean passed = new AtomicBoolean();

        filter.filter(exchange, ex -> {
            passed.set(true);
            return Mono.empty();
        }).block();

        assertTrue(passed.get());
    }

    @Test
    void missingToken_isRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chat/messages").build());
        AtomicBoolean passed = new AtomicBoolean();

        filter.filter(exchange, ex -> {
            passed.set(true);
            return Mono.empty();
        }).block();

        assertFalse(passed.get());
        assertEquals(200, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void validAccessToken_passes() {
        when(redisTemplate.hasKey(startsWith("token:blacklist:"))).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        String token = Jwts.builder()
                .subject("user-1")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/profile").header("Authorization", "Bearer " + token).build());
        AtomicBoolean passed = new AtomicBoolean();

        filter.filter(exchange, ex -> {
            passed.set(true);
            return Mono.empty();
        }).block();

        assertTrue(passed.get());
    }

    @Test
    void wsTicket_passesWhenPresent() {
        when(redisTemplate.hasKey("ws:ticket:abc")).thenReturn(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws?ticket=abc")
                        .header("Upgrade", "websocket")
                        .build());
        AtomicBoolean passed = new AtomicBoolean();

        filter.filter(exchange, ex -> {
            passed.set(true);
            return Mono.empty();
        }).block();

        assertTrue(passed.get());
    }
}
