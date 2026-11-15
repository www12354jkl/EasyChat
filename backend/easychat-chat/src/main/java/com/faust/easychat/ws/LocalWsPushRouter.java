package com.faust.easychat.ws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "easychat.ws.delivery", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalWsPushRouter implements WsPushRouter {

    private final WsChannelWriter channelWriter;

    @Override
    public void route(String toUserId, String type, Object payload) {
        channelWriter.sendToUser(toUserId, type, payload);
    }
}
