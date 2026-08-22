package com.faust.easychat.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.faust.easychat.dto.user.friend.FriendRequestDTO;
import com.faust.easychat.dto.user.friend.FriendRequestHandledDTO;
import com.faust.easychat.netty.WsMessageSender;
import com.faust.easychat.result.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/internal/ws")
@RequiredArgsConstructor
public class InternalWsController {

    private final WsMessageSender wsMessageSender;

    @PostMapping("/friend-request")
    public Result<Void> pushFriendRequest(@RequestParam String toUserId, @RequestBody FriendRequestDTO payload) {
        wsMessageSender.sendToUser(toUserId, "FRIEND_REQUEST", payload);
        return Result.success();
    }

    @PostMapping("/friend-request-handled")
    public Result<Void> pushFriendRequestHandled(@RequestParam String toUserId,
            @RequestBody FriendRequestHandledDTO payload) {
        wsMessageSender.sendToUser(toUserId, "FRIEND_REQUEST_HANDLED", payload);
        return Result.success();
    }
}
