package com.faust.easychat.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class ChatWsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WsMessageSender sender;

    public ChatWsHandler(WsMessageSender sender) {
        this.sender = sender;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        JsonNode node = MAPPER.readTree(frame.text());
        String type = node.path("type").asText();
        if ("PING".equals(type)) {
            sender.send(ctx.channel(), "PONG", null);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

}
