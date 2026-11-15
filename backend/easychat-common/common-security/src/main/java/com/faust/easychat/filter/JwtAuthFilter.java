package com.faust.easychat.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataAccessException;

import com.faust.easychat.context.UserContext;
import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.JwtUtil;
import com.faust.easychat.util.TokenUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwtAuthFilter implements Filter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final List<String> publicPrefixes;

    public JwtAuthFilter(JwtUtil jwtUtil, TokenBlacklistService tokenBlacklistService, String publicPaths) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.publicPrefixes = parsePublicPaths(publicPaths);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if (isPublic(path)) {
            chain.doFilter(req, resp);
            return;
        }

        if (hasMultipleAuthorizationHeaders(req)) {
            writeError(resp, "无效token");
            return;
        }

        String token = TokenUtils.extractBearerToken(req.getHeader("Authorization"));
        if (token == null) {
            writeError(resp, "未登录");
            return;
        }

        Claims claims;
        try {
            if (tokenBlacklistService.isBlacklisted(token)) {
                writeError(resp, "token无效或已过期");
                return;
            }
            claims = jwtUtil.parseToken(token);
            if (claims == null) {
                writeError(resp, "无效token");
                return;
            }
            if (jwtUtil.isRefreshToken(claims)) {
                writeError(resp, "无效token");
                return;
            }
            if (tokenBlacklistService.isRevokedByPasswordChange(claims.getSubject(), claims.getIssuedAt())) {
                writeError(resp, "token无效或已过期");
                return;
            }
        } catch (DataAccessException e) {
            log.warn("校验令牌时 Redis 不可用: {}", e.toString());
            writeServiceUnavailable(resp);
            return;
        } catch (ExpiredJwtException e) {
            writeError(resp, "token无效或已过期");
            return;
        } catch (RuntimeException e) {
            writeError(resp, "token无效或已过期");
            return;
        }

        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            writeError(resp, "无效token");
            return;
        }

        try {
            UserContext.set(userId);
            chain.doFilter(req, resp);
        } finally {
            UserContext.clear();
        }
    }

    private static List<String> parsePublicPaths(String publicPaths) {
        if (publicPaths == null || publicPaths.isBlank()) {
            return List.of();
        }
        return Arrays.stream(publicPaths.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();
    }

    private boolean isPublic(String path) {
        if (path == null || path.isEmpty() || isUnsafePath(path)) {
            return false;
        }
        for (String prefix : publicPrefixes) {
            if (matchesPrefix(path, prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMultipleAuthorizationHeaders(HttpServletRequest req) {
        Enumeration<String> headers = req.getHeaders("Authorization");
        if (headers == null || !headers.hasMoreElements()) {
            return false;
        }
        headers.nextElement();
        return headers.hasMoreElements();
    }

    private static boolean isUnsafePath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return path.contains("..")
                || path.contains("\\")
                || path.contains(";")
                || path.contains("//")
                || lower.contains("%2e")
                || lower.contains("%2f")
                || lower.contains("%5c");
    }

    private static boolean matchesPrefix(String path, String prefix) {
        if (prefix.endsWith("/")) {
            return path.startsWith(prefix);
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private void writeError(HttpServletResponse response, String msg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(200);
        response.getWriter().write("{\"code\":401,\"message\":\"" + msg + "\"}");
    }

    private void writeServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.getWriter().write("{\"code\":503,\"message\":\"认证服务暂不可用\"}");
    }
}
