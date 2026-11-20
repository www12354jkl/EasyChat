package com.faust.easychat.netty;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.faust.easychat.ws.WsOnlineStatusBroadcaster;
import com.faust.easychat.ws.WsSessionRegistry;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WsSessionManager {

    private static final int LOCK_STRIPES = 64;

    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();
    private final Object[] userLocks = createLocks();

    private final WsSessionRegistry sessionRegistry;
    private final WsOnlineStatusBroadcaster onlineStatusBroadcaster;

    private static Object[] createLocks() {
        Object[] locks = new Object[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    private Object lockFor(String userId) {
        return userLocks[Math.floorMod(userId.hashCode(), LOCK_STRIPES)];
    }

    public void register(String userId, Channel channel) {
        if (userId == null || userId.isBlank() || channel == null) {
            return;
        }
        Attribute<String> attr = channel.attr(AuthWsHandler.USER_ID);
        if (attr != null) {
            attr.set(userId);
        }
        Channel previous;
        // Serialize per-user session transitions so an older channel's unregister cannot outrace a newer register.
        synchronized (lockFor(userId)) {
            previous = channels.put(userId, channel);
            sessionRegistry.markOnline(userId);
            onlineStatusBroadcaster.broadcastAsync(userId, true);
        }
        if (previous != null && previous != channel) {
            previous.close();
        }
    }

    public void unregister(Channel channel) {
        if (channel == null) {
            return;
        }
        Attribute<String> attr = channel.attr(AuthWsHandler.USER_ID);
        String userId = attr != null ? attr.get() : null;
        if (userId == null || userId.isBlank()) {
            return;
        }
        synchronized (lockFor(userId)) {
            if (channels.remove(userId, channel)) {
                sessionRegistry.markOffline(userId);
                onlineStatusBroadcaster.broadcastAsync(userId, false);
            }
        }
    }

    public Channel getChannel(String userId) {
        if (userId == null) {
            return null;
        }
        return channels.get(userId);
    }

    public boolean isOnline(String userId) {
        if (userId == null) {
            return false;
        }
        return sessionRegistry.isOnline(userId);
    }
}
