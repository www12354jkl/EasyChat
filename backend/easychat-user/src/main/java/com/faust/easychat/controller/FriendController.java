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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "好友", description = "搜索用户、好友申请、好友列表")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "搜索用户")
    @GetMapping("/search")
    public Result<List<UserBriefDTO>> searchUser(
            @Parameter(description = "昵称或邮箱关键字", required = true) @RequestParam String keyword) {
        return Result.success(friendService.searchUser(keyword));
    }

    @Operation(summary = "发送好友申请")
    @PostMapping("/friend/request")
    public Result<Map<String, String>> addFriend(@RequestBody AddFriendRequest request) {
        String status = friendService.addFriend(request);
        return Result.success(Map.of("status", status));
    }

    @Operation(summary = "我收到的好友申请")
    @GetMapping("/friend/requests")
    public Result<List<FriendRequestDTO>> myRequests() {
        return Result.success(friendService.myRequests());
    }

    @Operation(summary = "处理好友申请", description = "status：1=同意，2=拒绝")
    @PutMapping("/friend/request/{id}")
    public Result<Void> handleRequest(
            @Parameter(description = "申请 id") @PathVariable String id,
            @RequestBody HandleRequestReq req) {
        friendService.handleRequest(id, req);
        return Result.success();
    }

    @Operation(summary = "好友列表")
    @GetMapping("/friends")
    public Result<List<FriendDTO>> getFriendList() {
        return Result.success(friendService.getFriendList());
    }

    @Operation(summary = "删除好友")
    @DeleteMapping("/friend/{id}")
    public Result<Void> deleteFriend(
            @Parameter(description = "好友用户 id") @PathVariable String id) {
        friendService.deleteFriend(id);
        return Result.success();
    }

}
