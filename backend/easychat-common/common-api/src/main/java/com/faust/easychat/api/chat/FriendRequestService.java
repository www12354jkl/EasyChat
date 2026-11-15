package com.faust.easychat.api.chat;

import com.faust.easychat.dto.user.friend.FriendRequestDTO;
import com.faust.easychat.dto.user.friend.FriendRequestHandledDTO;

public interface FriendRequestService {

    void pushFriendRequest(String toUserId, FriendRequestDTO payload);

    void pushFriendRequestHandled(String toUserId, FriendRequestHandledDTO payload);


}
