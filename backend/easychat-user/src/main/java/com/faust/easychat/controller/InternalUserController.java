package com.faust.easychat.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.result.Result;
import com.faust.easychat.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/user/{id}")
    public Result<UserBriefDTO> getUserById(@PathVariable String id) {
        return Result.success(userService.getUserBrief(id));
    }

    @GetMapping("/users/batch")
    public Result<List<UserBriefDTO>> batchGetUsers(@RequestParam("ids") String ids) {
        return Result.success(userService.batchGetUsers(ids));
    }



}
