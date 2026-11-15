package com.faust.easychat.api.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faust.easychat.api.chat.ConversationDeleteService;

/**
 * chat-service 不可用时不要假装删掉了会话，抛错让删好友事务回滚。
 */
public class ConversationDeleteServiceFallback implements ConversationDeleteService {

    private static final Logger log = LoggerFactory.getLogger(ConversationDeleteServiceFallback.class);

    @Override
    public void removeDirectConversation(String userId, String friendId) {
        log.warn("ConversationDeleteService 降级: removeDirectConversation userId={}, friendId={}",
                userId, friendId);
        throw new IllegalStateException("聊天服务暂不可用");
    }
}
