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
import com.faust.easychat.dto.user.LogoutRequest;
import com.faust.easychat.dto.user.RegisterRequest;
import com.faust.easychat.result.Result;
import com.faust.easychat.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@Tag(name = "认证服务", description = "登录、注册、验证码、刷新 token。除登出外均无需鉴权")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @SecurityRequirements
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "注册")
    @SecurityRequirements
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @Operation(summary = "忘记密码")
    @SecurityRequirements
    @PutMapping("/forget-password")
    public Result<Void> forgetPassword(@Valid @RequestBody ForgetPasswordRequest request) {
        authService.forgetPassword(request);
        return Result.success();
    }

    @Operation(summary = "发送邮箱验证码")
    @SecurityRequirements
    @PostMapping("/send-verify-code")
    public Result<Void> sendVerifyCode(
            @Parameter(description = "邮箱", required = true) @RequestParam String email,
            @Parameter(description = "register=注册，forgot=找回密码", required = true, example = "register") @RequestParam String type) {
        authService.sendVerifyCode(email, type);
        return Result.success();
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) LogoutRequest request) {
        String refreshToken = request != null ? request.getRefreshToken() : null;
        authService.logout(authHeader, refreshToken);
        return Result.success();
    }

    @Operation(summary = "刷新 accessToken", description = "请求体为 refreshToken 字符串，例如 \"eyJhbGciOi...\"")
    @SecurityRequirements
    @PostMapping("/token/refresh")
    public Result<String> refreshAccessToken(@RequestBody String refreshToken) {
        return Result.success(authService.refreshAccessToken(refreshToken));
    }
}
