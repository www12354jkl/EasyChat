package com.faust.easychat.controller;

import org.springframework.http.MediaType;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@Tag(name = "个人信息", description = "资料、头像、密码、加好友验证设置")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前用户资料")
    @GetMapping("/profile")
    public Result<ProfileDTO> getProfile() {
        String userId = UserContext.get();
        return Result.success(userService.getProfileById(userId));
    }

    @Operation(summary = "更新当前用户资料")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        String id = UserContext.get();
        userService.updateProfileById(id, request);
        return Result.success();
    }

    @Operation(summary = "上传头像")
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAvatar(
            @Parameter(description = "头像文件", required = true) @RequestParam("file") MultipartFile file) {
        String id = UserContext.get();
        return Result.success(userService.uploadAvatar(id, file));
    }

    @Operation(summary = "发送重置密码验证码")
    @PostMapping("/send-reset-code")
    public Result<Void> sendResetCode() {
        String userId = UserContext.get();
        userService.sendResetCode(userId);
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String userId = UserContext.get();
        userService.resetPassword(userId, request);
        return Result.success();
    }

    @Operation(summary = "修改加好友验证开关", description = "friendVerify：1=需要验证，0=允许任何人")
    @PutMapping("/friend-verify")
    public Result<Void> changeFriendVerify(@Valid @RequestBody SettingsRequest request) {
        String userId = UserContext.get();
        userService.updateFriendVerify(userId, request);
        return Result.success();
    }

    @Operation(summary = "获取当前用户设置")
    @GetMapping("/settings")
    public Result<SettingsRequest> getSettings() {
        String userId = UserContext.get();
        return Result.success(userService.getSettings(userId));
    }

    @Operation(summary = "按 id 查看用户公开信息")
    @GetMapping("/{userId}")
    public Result<UserInfoDTO> getUserInfo(
            @Parameter(description = "用户 id") @PathVariable String userId) {
        return Result.success(userService.getUserInfo(userId));
    }

}
