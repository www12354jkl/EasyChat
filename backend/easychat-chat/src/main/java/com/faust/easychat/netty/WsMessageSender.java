package com.faust.easychat.netty;

import org.springframework.stereotype.Component;

import com.faust.easychat.ws.WsChannelWriter;
import com.faust.easychat.ws.WsPushRouter;

import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WsMessageSender {

    private final WsPushRouter pushRouter;
    private final WsChannelWriter channelWriter;

    public void sendToUser(String userId, String type, Object payload) {
        pushRouter.route(userId, type, payload);
    }

    public void send(Channel channel, String type, Object payload) {
        channelWriter.send(channel, type, payload);
    }
}
