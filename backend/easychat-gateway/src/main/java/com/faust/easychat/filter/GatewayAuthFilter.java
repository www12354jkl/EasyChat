package com.faust.easychat.filter;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String REVOKE_PREFIX = "token:revoke-before:";
    private static final String TICKET_PREFIX = "ws:ticket:";
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/token/refresh",
            "/api/auth/send-verify-code",
            "/api/auth/forget-password",
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars");

    private final StringRedisTemplate redisTemplate;
    private final SecretKey signingKey;

    public GatewayAuthFilter(StringRedisTemplate redisTemplate, @Value("${jwt.secret}") String secret) {
        this.redisTemplate = redisTemplate;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = traceIdOf(exchange.getRequest());
        ServerWebExchange traced = exchange.mutate()
                .request(exchange.getRequest().mutate().header("X-Request-Id", traceId).build())
                .build();
        ServerHttpRequest request = traced.getRequest();
        if (request.getMethod() == HttpMethod.OPTIONS || isPublic(request.getPath().value())) {
            return chain.filter(traced);
        }
        if (request.getPath().value().startsWith("/ws")) {
            return allowWsTicket(traced, chain, request);
        }
        String token = tokenOf(request);
        if (token == null) {
            return deny(traced, "未登录");
        }
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token))) {
                return deny(traced, "token无效或已过期");
            }
            Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            if ("refresh".equals(claims.get("type", String.class))) {
                return deny(traced, "无效token");
            }
            if (revoked(claims.getSubject(), claims.getIssuedAt())) {
                return deny(traced, "token无效或已过期");
            }
            log.info("access {} {} userId={} traceId={}", request.getMethod(), request.getPath().value(),
                    claims.getSubject(), traceId);
            return chain.filter(traced);
        } catch (DataAccessException e) {
            log.warn("网关校验令牌时 Redis 不可用: {}", e.toString());
            return unavailable(traced);
        } catch (RuntimeException e) {
            return deny(traced, "token无效或已过期");
        }
    }

    private Mono<Void> allowWsTicket(ServerWebExchange exchange, GatewayFilterChain chain, ServerHttpRequest request) {
        String ticket = request.getQueryParams().getFirst("ticket");
        if (ticket == null || ticket.isBlank()) {
            return deny(exchange, "未登录");
        }
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(TICKET_PREFIX + ticket.trim()))) {
                return deny(exchange, "连接票据无效");
            }
            ServerWebExchange withTicket = exchange.mutate()
                    .request(request.mutate().header("X-Ws-Ticket", ticket.trim()).build())
                    .build();
            return chain.filter(withTicket);
        } catch (DataAccessException e) {
            log.warn("网关校验连接票据时 Redis 不可用: {}", e.toString());
            return unavailable(exchange);
        }
    }

    private static String traceIdOf(ServerHttpRequest request) {
        String incoming = request.getHeaders().getFirst("X-Request-Id");
        if (incoming == null || incoming.isBlank() || incoming.length() > 64) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return incoming.trim();
    }

    private boolean revoked(String userId, Date issuedAt) {
        if (userId == null || userId.isBlank() || issuedAt == null) {
            return false;
        }
        String raw = redisTemplate.opsForValue().get(REVOKE_PREFIX + userId);
        if (raw == null) {
            return false;
        }
        try {
            return issuedAt.getTime() <= Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isPublic(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String tokenOf(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }

    private Mono<Void> deny(ServerWebExchange exchange, String message) {
        boolean websocket = exchange.getRequest().getHeaders().getFirst(HttpHeaders.UPGRADE) != null;
        exchange.getResponse().setStatusCode(websocket ? HttpStatus.UNAUTHORIZED : HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"code\":401,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> unavailable(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":503,\"message\":\"认证服务暂不可用\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
