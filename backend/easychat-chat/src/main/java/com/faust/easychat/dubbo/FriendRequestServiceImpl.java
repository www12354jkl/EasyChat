package com.faust.easychat.dubbo;


import com.faust.easychat.api.chat.FriendRequestService;
import com.faust.easychat.dto.user.friend.FriendRequestDTO;
import com.faust.easychat.dto.user.friend.FriendRequestHandledDTO;
import com.faust.easychat.netty.WsMessageSender;

import org.apache.dubbo.config.annotation.DubboService;

import lombok.RequiredArgsConstructor;

@DubboService
@RequiredArgsConstructor
public class FriendRequestServiceImpl implements FriendRequestService {

    private final WsMessageSender wsMessageSender;

    @Override
    public void pushFriendRequest(String toUserId, FriendRequestDTO payload) {
        wsMessageSender.sendToUser(toUserId, "FRIEND_REQUEST", payload);
    }

    @Override
    public void pushFriendRequestHandled(String toUserId, FriendRequestHandledDTO payload) {
        wsMessageSender.sendToUser(toUserId, "FRIEND_REQUEST_HANDLED", payload);
    }

}
