package com.faust.easychat.netty;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.faust.easychat.ws.WsOnlineStatusBroadcaster;
import com.faust.easychat.ws.WsSessionRegistry;

import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WsSessionManager {

    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();

    private final WsSessionRegistry sessionRegistry;
    private final WsOnlineStatusBroadcaster onlineStatusBroadcaster;

    public void register(String userId, Channel channel) {
        channels.put(userId, channel);
        sessionRegistry.markOnline(userId);
        onlineStatusBroadcaster.broadcastAsync(userId, true);
    }

    public void unregister(Channel channel) {
        String userId = channel.attr(AuthWsHandler.USER_ID).get();
        if (userId != null) {
            channels.remove(userId, channel);
            sessionRegistry.markOffline(userId);
            onlineStatusBroadcaster.broadcastAsync(userId, false);
        }
    }

    public Channel getChannel(String userId) {
        return channels.get(userId);
    }

    public boolean isOnline(String userId) {
        return sessionRegistry.isOnline(userId);
    }
}
