package com.faust.easychat.service;

import com.faust.easychat.dto.user.AuthResponse;
import com.faust.easychat.dto.user.ForgetPasswordRequest;
import com.faust.easychat.dto.user.LoginRequest;
import com.faust.easychat.dto.user.RegisterRequest;

public interface AuthService {

    void sendVerifyCode(String email, String type);

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    void forgetPassword(ForgetPasswordRequest request);

    void logout(String authHeader, String refreshToken);

    String refreshAccessToken(String refreshToken);

}
