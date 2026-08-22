package com.faust.easychat.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.dto.user.friend.AddFriendRequest;
import com.faust.easychat.dto.user.friend.FriendDTO;
import com.faust.easychat.dto.user.friend.FriendRequestDTO;
import com.faust.easychat.dto.user.friend.HandleRequestReq;
import com.faust.easychat.result.Result;
import com.faust.easychat.service.FriendService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "好友")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @GetMapping("/search")
    public Result<List<UserBriefDTO>> searchUser(@RequestParam(required = true) String keyword) {
        return Result.success(friendService.searchUser(keyword));
    }

    @PostMapping("/friend/request")
    public Result<Map<String, String>> addFriend(@RequestBody AddFriendRequest request) {
        String status = friendService.addFriend(request);
        return Result.success(Map.of("status", status));
    }

    @GetMapping("/friend/requests")
    public Result<List<FriendRequestDTO>> myRequests() {
        return Result.success(friendService.myRequests());
    }

    @PutMapping("/friend/request/{id}")
    public Result<Void> handleRequest(@PathVariable String id, @RequestBody HandleRequestReq req) {
        friendService.handleRequest(id, req);
        return Result.success();
    }

    @GetMapping("/friends")
    public Result<List<FriendDTO>> getFriendList() {
        return Result.success(friendService.getFriendList());
    }

    @DeleteMapping("/friend/{id}")
    public Result<Void> deleteFriend(@PathVariable String id) {
        friendService.deleteFriend(id);
        return Result.success();
    }

}
