package com.faust.easychat.service;

import java.util.List;

import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.dto.user.friend.AddFriendRequest;
import com.faust.easychat.dto.user.friend.FriendDTO;
import com.faust.easychat.dto.user.friend.FriendRequestDTO;
import com.faust.easychat.dto.user.friend.HandleRequestReq;

public interface FriendService {

    List<UserBriefDTO> searchUser(String keyword);

    /** 返回 'added'（对方无需验证直接加成功）或 'pending'（已发送等待同意） */
    String addFriend(AddFriendRequest request);

    List<FriendRequestDTO> myRequests();

    void handleRequest(String id, HandleRequestReq req);

    List<FriendDTO> getFriendList();

    void deleteFriend(String id);

}