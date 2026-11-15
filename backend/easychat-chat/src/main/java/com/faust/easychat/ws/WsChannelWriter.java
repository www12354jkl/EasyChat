package com.faust.easychat.ws;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.faust.easychat.netty.WsSessionManager;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

@Component
@Slf4j
public class WsChannelWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final WsSessionManager sessionManager;

    public WsChannelWriter(@Lazy WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void sendToUser(String userId, String type, Object payload) {
        Channel channel = sessionManager.getChannel(userId);
        boolean delivered = channel != null && channel.isActive();
        log.info("ws push type={} toUserId={} delivered={} traceId={}", type, userId, delivered, MDC.get("traceId"));
        send(channel, type, payload);
    }

    public void send(Channel channel, String type, Object payload) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        ObjectNode frame = MAPPER.createObjectNode();
        frame.put("type", type);
        if (payload == null) {
            frame.putNull("payload");
        } else {
            frame.set("payload", MAPPER.valueToTree(payload));
        }
        channel.writeAndFlush(new TextWebSocketFrame(frame.toString()));
    }
}
