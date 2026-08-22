package com.faust.easychat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.faust.easychat.context.UserContext;
import com.faust.easychat.dto.user.ProfileDTO;
import com.faust.easychat.dto.user.ResetPasswordRequest;
import com.faust.easychat.dto.user.SettingsRequest;
import com.faust.easychat.dto.user.UpdateProfileRequest;
import com.faust.easychat.dto.user.UserInfoDTO;
import com.faust.easychat.result.Result;
import com.faust.easychat.service.UserService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@Tag(name = "个人信息")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public Result<ProfileDTO> getProfile() {
        String userId = UserContext.get();
        return Result.success(userService.getProfileById(userId));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        String id = UserContext.get();
        userService.updateProfileById(id, request);
        return Result.success();
    }

    @PutMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String id = UserContext.get();
        return Result.success(userService.uploadAvatar(id, file));
    }

    @PostMapping("/send-reset-code")
    public Result<Void> sendResetCode() {
        String userId = UserContext.get();
        userService.sendResetCode(userId);
        return Result.success();
    }

    @PutMapping("/password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String userId = UserContext.get();
        userService.resetPassword(userId, request);
        return Result.success();
    }

    @PutMapping("/friend-verify")
    public Result<Void> changeFriendVerify(@Valid @RequestBody SettingsRequest request) {
        String userId = UserContext.get();
        userService.updateFriendVerify(userId, request);
        return Result.success();
    }

    @GetMapping("/settings")
    public Result<SettingsRequest> getSettings() {
        String userId = UserContext.get();
        return Result.success(userService.getSettings(userId));
    }

    @GetMapping("/{userId}")
    public Result<UserInfoDTO> getUserInfo(@Valid @PathVariable String userId) {
        return Result.success(userService.getUserInfo(userId));
    }

}
