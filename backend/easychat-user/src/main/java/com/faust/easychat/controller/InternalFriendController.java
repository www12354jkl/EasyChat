package com.faust.easychat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.faust.easychat.mapper.FriendMapper;
import com.faust.easychat.result.Result;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Hidden
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalFriendController {

    private final FriendMapper friendMapper;

    @GetMapping("/friend/friend-confirm")
    public Result<Boolean> isFriend(@RequestParam("userId") String userId,@RequestParam("friendId") String friendId) {
        if (friendMapper.selectByUserIdAndFriendUserId(userId, friendId) != null) {
            return Result.success(true);
        } else {
            return Result.success(false);
        }
    }
}
