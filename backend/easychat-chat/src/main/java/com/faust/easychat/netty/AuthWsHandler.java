package com.faust.easychat.netty;

import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@RequiredArgsConstructor
public class AuthWsHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<String> USER_ID = AttributeKey.valueOf("userId");

    private static final String WS_PATH = "/ws";

    private final JwtUtil jwtUtil;

    private final TokenBlacklistService tokenBlacklistService;

    private final WsSessionManager sessionManager;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());

        // 路径不匹配
        if (!WS_PATH.equals(decoder.path())) {
            sendError(ctx, request, HttpResponseStatus.NOT_FOUND, "Not Found");
            return;
        }

        // 从 URL 参数取 token
        List<String> tokens = decoder.parameters().get("token");
        if (tokens == null || tokens.isEmpty()) {
            sendError(ctx, request, HttpResponseStatus.UNAUTHORIZED, "Missing token");
            return;
        }

        try {
            String token = tokens.get(0);
            if (tokenBlacklistService.isBlacklisted(token)) {
                sendError(ctx, request, HttpResponseStatus.UNAUTHORIZED, "Token revoked");
                return;
            }
            Claims claims = jwtUtil.parseToken(token);

            // 禁止用 Refresh Token 建立连接
            if (jwtUtil.isRefreshToken(claims)) {
                sendError(ctx, request, HttpResponseStatus.UNAUTHORIZED, "Refresh token not allowed");
                return;
            }
            String userId = claims.getSubject();

            // 绑定用户ID到 Channel
            ctx.channel().attr(USER_ID).set(userId);

            sessionManager.register(userId, ctx.channel());

            ctx.fireChannelRead(msg);

        } catch (ExpiredJwtException e) {
            sendError(ctx, request, HttpResponseStatus.UNAUTHORIZED, "Token expired");
        } catch (JwtException | IllegalArgumentException e) {
            sendError(ctx, request, HttpResponseStatus.UNAUTHORIZED, "Invalid token");
        }
    }

    private void sendError(ChannelHandlerContext ctx, FullHttpRequest request,
                           HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1,
                status,
                Unpooled.copiedBuffer(message, StandardCharsets.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        // ===== 关键：释放 request，避免内存泄漏 =====
        request.release();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        sessionManager.unregister(ctx.channel());
    }
}
