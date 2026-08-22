package com.faust.easychat.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.faust.easychat.dto.user.friend.FriendRequestDTO;
import com.faust.easychat.dto.user.friend.FriendRequestHandledDTO;
import com.faust.easychat.feign.fallback.ChatFeignClientFallbackFactory;
import com.faust.easychat.result.Result;

@FeignClient(value = "easychat-chat-service", fallbackFactory = ChatFeignClientFallbackFactory.class)
public interface ChatFeignClient {

    @DeleteMapping("/api/internal/conversation/direct")
    Result<Void> removeDirectConversation(@RequestParam("userId") String userId,
            @RequestParam("friendId") String friendId);

    @PostMapping("/api/internal/ws/friend-request")
    Result<Void> pushFriendRequest(@RequestParam("toUserId") String toUserId,
            @RequestBody FriendRequestDTO payload);

    @PostMapping("/api/internal/ws/friend-request-handled")
    Result<Void> pushFriendRequestHandled(@RequestParam("toUserId") String toUserId,
            @RequestBody FriendRequestHandledDTO payload);
}
