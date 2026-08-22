package com.faust.easychat.netty;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class WsMessageSender {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final WsSessionManager sessionManager;

    public void sendToUser(String userId, String type, Object payload) {
        send(sessionManager.getChannel(userId), type, payload);
    }

    public void send(Channel channel, String type, Object payload) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        ObjectNode frame = MAPPER.createObjectNode();
        frame.put("type", type);
        frame.set("payload", MAPPER.valueToTree(payload));
        channel.writeAndFlush(new TextWebSocketFrame(frame.toString()));
    }
}
