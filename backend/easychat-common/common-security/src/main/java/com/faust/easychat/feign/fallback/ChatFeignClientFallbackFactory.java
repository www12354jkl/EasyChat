package com.faust.easychat.feign.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.faust.easychat.dto.user.friend.FriendRequestDTO;
import com.faust.easychat.dto.user.friend.FriendRequestHandledDTO;
import com.faust.easychat.feign.ChatFeignClient;
import com.faust.easychat.result.Result;

@Component
public class ChatFeignClientFallbackFactory implements FallbackFactory<ChatFeignClient> {

    @Override
    public ChatFeignClient create(Throwable cause) {
        return new ChatFeignClient() {
            @Override
            public Result<Void> removeDirectConversation(String userId, String friendId) {
                return Result.error(500, "聊天服务暂不可用");
            }

            @Override
            public Result<Void> pushFriendRequest(String toUserId, FriendRequestDTO payload) {
                return Result.error(500, "聊天服务暂不可用");
            }

            @Override
            public Result<Void> pushFriendRequestHandled(String toUserId, FriendRequestHandledDTO payload) {
                return Result.error(500, "聊天服务暂不可用");
            }
        };
    }
}
