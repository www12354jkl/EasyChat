package com.faust.easychat.api.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faust.easychat.api.chat.ConversationDeleteService;

/**
 * chat-service 不可用时的会话清理降级：静默跳过，好友关系仍已删除。
 */
public class ConversationDeleteServiceFallback implements ConversationDeleteService {

    private static final Logger log = LoggerFactory.getLogger(ConversationDeleteServiceFallback.class);

    @Override
    public void removeDirectConversation(String userId, String friendId) {
        log.warn("ConversationDeleteService 降级: removeDirectConversation userId={}, friendId={}",
                userId, friendId);
    }
}
