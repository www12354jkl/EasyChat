package com.faust.easychat.filter;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Reactive gateway authentication filter validating Bearer JWTs (blacklist, token type,
 * and {@code token:revoke-before} cutoff) and WebSocket one-time tickets against Redis.
 */
@Slf4j
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String REVOKE_PREFIX = "token:revoke-before:";
    private static final String TICKET_PREFIX = "ws:ticket:";
    private static final String BEARER_PREFIX = "Bearer";
    private static final Set<String> PUBLIC_EXACT_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/token/refresh",
            "/api/auth/send-verify-code",
            "/api/auth/forget-password",
            "/swagger-ui.html");
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final JwtParser jwtParser;

    public GatewayAuthFilter(ReactiveStringRedisTemplate redisTemplate, @Value("${jwt.secret}") String secret) {
        this.redisTemplate = redisTemplate;
        SecretKey signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser().verifyWith(signingKey).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = traceIdOf(exchange.getRequest());
        ServerWebExchange traced = withHeader(exchange, "X-Request-Id", traceId);
        ServerHttpRequest request = traced.getRequest();
        String path = request.getPath().value();
        String rawPath = request.getURI().getRawPath();

        if (request.getMethod() == HttpMethod.OPTIONS || isPublic(path, rawPath)) {
            return chain.filter(traced);
        }
        if (matchesSegmentPrefix(path, "/ws") && !hasUnsafePathSyntax(path, rawPath)) {
            return allowWsTicket(traced, chain, request);
        }
        return authenticateBearer(traced, chain, request, traceId);
    }

    private Mono<Void> authenticateBearer(ServerWebExchange traced, GatewayFilterChain chain,
            ServerHttpRequest request, String traceId) {
        String token = tokenOf(request);
        if (token == null) {
            return deny(traced, "未登录");
        }
        // Keep downstream chain.filter outside verifyToken's onErrorResume so downstream errors are never masked as 401/503.
        return verifyToken(token)
                .flatMap(decision -> applyDecision(traced, chain, decision, () -> {
                    log.info("access {} {} userId={} traceId={}", request.getMethod(), request.getPath().value(),
                            decision.userId(), traceId);
                    return withHeader(traced, HttpHeaders.AUTHORIZATION, "Bearer " + token);
                }));
    }

    private Mono<AuthDecision> verifyToken(String token) {
        return hasRedisKey(BLACKLIST_PREFIX + token)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        return Mono.just(AuthDecision.deny("token无效或已过期"));
                    }
                    Claims claims = jwtParser.parseSignedClaims(token).getPayload();
                    String type = claims.get("type", String.class);
                    if ("refresh".equals(type) || (type != null && !"access".equals(type))) {
                        return Mono.just(AuthDecision.deny("无效token"));
                    }
                    String userId = claims.getSubject();
                    if (userId == null || userId.isBlank()) {
                        return Mono.just(AuthDecision.deny("无效token"));
                    }
                    return isRevoked(userId, claims.getIssuedAt())
                            .map(revoked -> revoked
                                    ? AuthDecision.deny("token无效或已过期")
                                    : AuthDecision.allow(userId));
                })
                .onErrorResume(DataAccessException.class, e -> {
                    log.warn("网关校验令牌时 Redis 不可用: {}", e.toString());
                    return Mono.just(AuthDecision.serviceUnavailable());
                })
                .onErrorResume(RuntimeException.class, e -> Mono.just(AuthDecision.deny("token无效或已过期")));
    }

    private Mono<Void> allowWsTicket(ServerWebExchange traced, GatewayFilterChain chain, ServerHttpRequest request) {
        String ticket = request.getQueryParams().getFirst("ticket");
        if (ticket == null || ticket.isBlank()) {
            return deny(traced, "未登录");
        }
        String trimmedTicket = ticket.trim();
        return hasRedisKey(TICKET_PREFIX + trimmedTicket)
                .map(exists -> exists ? AuthDecision.allow(null) : AuthDecision.deny("连接票据无效"))
                .onErrorResume(DataAccessException.class, e -> {
                    log.warn("网关校验连接票据时 Redis 不可用: {}", e.toString());
                    return Mono.just(AuthDecision.serviceUnavailable());
                })
                .flatMap(decision -> applyDecision(traced, chain, decision,
                        () -> withHeader(traced, "X-Ws-Ticket", trimmedTicket)));
    }

    private Mono<Void> applyDecision(ServerWebExchange traced, GatewayFilterChain chain,
            AuthDecision decision, Supplier<ServerWebExchange> nextExchange) {
        if (decision.unavailable()) {
            return unavailable(traced);
        }
        if (!decision.allowed()) {
            return deny(traced, decision.message());
        }
        return chain.filter(nextExchange.get());
    }

    private Mono<Boolean> hasRedisKey(String key) {
        return Mono.defer(() -> {
            Mono<Boolean> exists = redisTemplate.hasKey(key);
            return exists == null ? Mono.just(false) : exists.defaultIfEmpty(false);
        });
    }

    private Mono<Boolean> isRevoked(String userId, Date issuedAt) {
        return Mono.defer(() -> {
            ReactiveValueOperations<String, String> ops = redisTemplate.opsForValue();
            Mono<String> rawMono = ops == null ? null : ops.get(REVOKE_PREFIX + userId);
            if (rawMono == null) {
                return Mono.just(false);
            }
            return rawMono
                    .map(raw -> isIssuedAtOrBefore(userId, issuedAt, raw))
                    .defaultIfEmpty(false);
        });
    }

    private static boolean isIssuedAtOrBefore(String userId, Date issuedAt, String raw) {
        if (issuedAt == null) {
            return true;
        }
        try {
            return issuedAt.getTime() <= Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid revoke-before timestamp for userId={}: {}", userId, raw);
            return false;
        }
    }

    private static ServerWebExchange withHeader(ServerWebExchange exchange, String name, String value) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate().header(name, value).build())
                .build();
    }

    private static String traceIdOf(ServerHttpRequest request) {
        String incoming = request.getHeaders().getFirst("X-Request-Id");
        if (incoming == null || incoming.isBlank() || incoming.length() > 64
                || incoming.indexOf('\r') >= 0 || incoming.indexOf('\n') >= 0) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return incoming.trim();
    }

    private static boolean isPublic(String path, String rawPath) {
        if (hasUnsafePathSyntax(path, rawPath)) {
            return false;
        }
        if (PUBLIC_EXACT_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (matchesSegmentPrefix(path, prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSegmentPrefix(String path, String prefix) {
        return path != null && (path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private static boolean hasUnsafePathSyntax(String path, String rawPath) {
        if (path == null || path.isEmpty() || path.indexOf(';') >= 0
                || path.contains("/../") || path.endsWith("/..")
                || path.contains("/./") || path.endsWith("/.")) {
            return true;
        }
        if (rawPath == null) {
            return false;
        }
        String lowerRaw = rawPath.toLowerCase(Locale.ROOT);
        return rawPath.indexOf(';') >= 0 || rawPath.indexOf('\\') >= 0
                || lowerRaw.contains("%2e") || lowerRaw.contains("%2f");
    }

    private static String tokenOf(ServerHttpRequest request) {
        List<String> headers = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (headers == null || headers.size() != 1) {
            return null;
        }
        String trimmed = headers.get(0).trim();
        if (trimmed.length() <= BEARER_PREFIX.length()
                || !trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                || !Character.isWhitespace(trimmed.charAt(BEARER_PREFIX.length()))) {
            return null;
        }
        String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || containsWhitespace(token)) {
            return null;
        }
        return token;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> deny(ServerWebExchange exchange, String message) {
        // Preserve HTTP 200 + {"code":401,...} contract expected by frontend request wrapper.
        boolean websocket = exchange.getRequest().getHeaders().getFirst(HttpHeaders.UPGRADE) != null;
        HttpStatus status = websocket ? HttpStatus.UNAUTHORIZED : HttpStatus.OK;
        return writeJsonError(exchange, status, "{\"code\":401,\"message\":\"" + message + "\"}");
    }

    private Mono<Void> unavailable(ServerWebExchange exchange) {
        return writeJsonError(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                "{\"code\":503,\"message\":\"认证服务暂不可用\"}");
    }

    private static Mono<Void> writeJsonError(ServerWebExchange exchange, HttpStatus status, String json) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private record AuthDecision(boolean allowed, boolean unavailable, String userId, String message) {
        private static AuthDecision allow(String userId) {
            return new AuthDecision(true, false, userId, null);
        }

        private static AuthDecision deny(String message) {
            return new AuthDecision(false, false, null, message);
        }

        private static AuthDecision serviceUnavailable() {
            return new AuthDecision(false, true, null, null);
        }
    }
}
