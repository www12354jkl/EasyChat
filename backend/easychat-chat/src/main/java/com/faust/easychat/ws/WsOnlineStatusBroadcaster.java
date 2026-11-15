package com.faust.easychat.ws;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;

import com.faust.easychat.mapper.ConversationMemberMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsOnlineStatusBroadcaster {

    private final ConversationMemberMapper conversationMemberMapper;
    private final WsPushRouter pushRouter;

    public void broadcast(String userId, boolean online) {
        List<String> peers;
        try {
            peers = conversationMemberMapper.selectPeerIds(userId);
        } catch (Exception e) {
            log.warn("查询会话成员失败，跳过在线状态广播: userId={}", userId, e);
            return;
        }
        Map<String, Object> payload = Map.of("userId", userId, "online", online);
        for (String peer : peers) {
            pushRouter.route(peer, "ONLINE_STATUS", payload);
        }
    }

    public void broadcastAsync(String userId, boolean online) {
        CompletableFuture.runAsync(() -> broadcast(userId, online));
    }
}
