package com.faust.easychat.netty;

import com.faust.easychat.service.WsTicketService;
import com.faust.easychat.netty.WsSessionManager;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@Slf4j
@RequiredArgsConstructor
public class AuthWsHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<String> USER_ID = AttributeKey.valueOf("userId");

    private static final String WS_PATH = "/ws";

    private final WsTicketService wsTicketService;

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

        String ticket = request.headers().get("X-Ws-Ticket");
        if (ticket == null || ticket.isBlank()) {
            List<String> tickets = decoder.parameters().get("ticket");
            if (tickets != null && !tickets.isEmpty()) {
                ticket = tickets.get(0);
            }
        }
        if (ticket == null || ticket.isBlank()) {
            log.warn("ws handshake missing ticket path={}", decoder.path());
            sendError(ctx, request, HttpResponseStatus.UNAUTHORIZED, "Missing ticket");
            return;
        }
        String userId = wsTicketService.consume(ticket);
        if (userId == null || userId.isBlank()) {
            log.warn("ws handshake ticket rejected");
            sendError(ctx, request, HttpResponseStatus.UNAUTHORIZED, "Invalid ticket");
            return;
        }
        ctx.channel().attr(USER_ID).set(userId);
        sessionManager.register(userId, ctx.channel());
        ctx.fireChannelRead(msg);
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
