package com.faust.easychat.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.faust.easychat.dto.user.AuthResponse;
import com.faust.easychat.dto.user.ForgetPasswordRequest;
import com.faust.easychat.dto.user.LoginRequest;
import com.faust.easychat.dto.user.RegisterRequest;
import com.faust.easychat.result.Result;
import com.faust.easychat.service.AuthService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@Tag(name = "认证服务")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PutMapping("/forget-password")
    public Result<Void> forgetPassword(@Valid @RequestBody ForgetPasswordRequest request) {
        authService.forgetPassword(request);
        return Result.success();
    }

    @PostMapping("/send-verify-code")
    public Result<Void> sendVerifyCode(@RequestParam String email, @RequestParam String type) {
        authService.sendVerifyCode(email, type);
        return Result.success();
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return Result.success();
    }

    @PostMapping("/token/refresh")
    public Result<String> refreshAccessToken(@RequestBody String refreshToken) {
        return Result.success(authService.refreshAccessToken(refreshToken));
    }
}
