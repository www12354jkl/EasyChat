package com.faust.easychat.api.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faust.easychat.api.chat.FriendRequestService;
import com.faust.easychat.dto.user.friend.FriendRequestDTO;
import com.faust.easychat.dto.user.friend.FriendRequestHandledDTO;

/**
 * chat-service 不可用时的 WS 推送降级：静默跳过，不影响好友主流程。
 */
public class FriendRequestServiceFallback implements FriendRequestService {

    private static final Logger log = LoggerFactory.getLogger(FriendRequestServiceFallback.class);

    @Override
    public void pushFriendRequest(String toUserId, FriendRequestDTO payload) {
        log.warn("FriendRequestService 降级: pushFriendRequest toUserId={}, requestId={}",
                toUserId, payload == null ? null : payload.getId());
    }

    @Override
    public void pushFriendRequestHandled(String toUserId, FriendRequestHandledDTO payload) {
        log.warn("FriendRequestService 降级: pushFriendRequestHandled toUserId={}, requestId={}",
                toUserId, payload == null ? null : payload.getId());
    }
}
