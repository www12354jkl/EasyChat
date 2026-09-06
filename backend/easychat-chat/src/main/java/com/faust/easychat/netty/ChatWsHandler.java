package com.faust.easychat.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.faust.easychat.ws.WsSessionRegistry;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class ChatWsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WsMessageSender sender;
    private final WsSessionRegistry sessionRegistry;

    public ChatWsHandler(WsMessageSender sender, WsSessionRegistry sessionRegistry) {
        this.sender = sender;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        JsonNode node = MAPPER.readTree(frame.text());
        String type = node.path("type").asText();
        if ("PING".equals(type)) {
            String userId = ctx.channel().attr(AuthWsHandler.USER_ID).get();
            if (userId != null) {
                sessionRegistry.refresh(userId);
            }
            sender.send(ctx.channel(), "PONG", null);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
