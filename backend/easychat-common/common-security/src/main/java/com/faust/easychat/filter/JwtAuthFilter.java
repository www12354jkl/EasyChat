package com.faust.easychat.filter;

import java.io.IOException;

import com.faust.easychat.context.UserContext;
import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.JwtUtil;

import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter implements Filter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final String publicPaths;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if(isPublic(path)) {
            chain.doFilter(req, resp);
            return;
        }

        String authHeader = req.getHeader("Authorization");

        if(authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(resp, "未登录");
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (tokenBlacklistService.isBlacklisted(token)) {
                writeError(resp, "token无效或已过期");
                return;
            }
            Claims claims = jwtUtil.parseToken(token);
            if (jwtUtil.isRefreshToken(claims)) {
                writeError(resp, "无效token");
                return;
            }
            UserContext.set(claims.getSubject());
            chain.doFilter(req, resp);
        } catch (Exception e) {
            writeError(resp, "token无效或已过期");
        } finally {
            UserContext.clear();
        }
    }

    private boolean isPublic(String path) {
        for (String p : publicPaths.split(",")) {
            if (path.startsWith(p.trim())) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, String msg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(200);
        response.getWriter().write("{\"code\":401,\"message\":\"" + msg + "\"}");
    }
}
