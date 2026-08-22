package com.faust.easychat.netty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.faust.easychat.mapper.ConversationMemberMapper;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Component
@Slf4j
@RequiredArgsConstructor
public class WsSessionManager {

    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final ConversationMemberMapper conversationMemberMapper;

    public void register(String userId, Channel channel) {
        channels.put(userId, channel);
        CompletableFuture.runAsync(() -> broadcastOnlineStatus(userId, true));
    }

    public void unregister(Channel channel) {
        String userId = channel.attr(AuthWsHandler.USER_ID).get();
        if (userId != null) {
            channels.remove(userId, channel);
            CompletableFuture.runAsync(() -> broadcastOnlineStatus(userId, false));
        }
    }

    public Channel getChannel(String userId) {
        return channels.get(userId);
    }

    public boolean isOnline(String userId) {
        return channels.containsKey(userId);
    }

    /** 上线/下线时，通知该用户所有会话里的其他成员，让前端在线状态绿点实时刷新 */
    private void broadcastOnlineStatus(String userId, boolean online) {
        List<String> peers;
        try {
            peers = conversationMemberMapper.selectPeerIds(userId);
        } catch (Exception e) {
            log.warn("查询会话成员失败，跳过在线状态广播: userId={}", userId, e);
            return;
        }
        for (String peer : peers) {
            Channel ch = channels.get(peer);
            if (ch == null || !ch.isActive()) {
                continue;
            }
            ObjectNode frame = MAPPER.createObjectNode();
            frame.put("type", "ONLINE_STATUS");
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("userId", userId);
            payload.put("online", online);
            frame.set("payload", payload);
            ch.writeAndFlush(new TextWebSocketFrame(frame.toString()));
        }
    }

}
