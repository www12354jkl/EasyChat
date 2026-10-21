package com.faust.easychat.service.impl;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;

import com.faust.easychat.dto.user.AuthResponse;
import com.faust.easychat.dto.user.ForgetPasswordRequest;
import com.faust.easychat.dto.user.LoginRequest;
import com.faust.easychat.dto.user.RegisterRequest;
import com.faust.easychat.dto.user.TokenRefreshResponse;
import com.faust.easychat.entity.User;
import com.faust.easychat.entity.UserSettings;
import com.faust.easychat.mapper.AuthMapper;
import com.faust.easychat.mapper.UserSettingsMapper;
import com.faust.easychat.service.AuthService;
import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.EmailUtil;
import com.faust.easychat.util.JwtUtil;
import com.faust.easychat.exception.AppException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    
    private final AuthMapper authMapper;

    private final UserSettingsMapper userSettingsMapper;

    private final JwtUtil jwtUtil;

    private final EmailUtil emailUtil;

    private final StringRedisTemplate redisTemplate;

    private final TokenBlacklistService tokenBlacklistService;

    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void sendVerifyCode(String email, String type) {
        // 60s 发送间隔防刷（独立 key，过期即放行）
        String limitKey = "email:code:limit:" + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            throw AppException.tooManyRequests("发送过于频繁，请稍后再试");
        }
        // 按场景校验邮箱存在性：register 要求未注册，forgot 要求已注册
        User user = authMapper.selectByEmail(email);
        if ("register".equals(type)) {
            if (user != null) {
                throw AppException.emailExists();
            }
        } else if ("forgot".equals(type)) {
            if (user == null) {
                throw AppException.userNotFound();
            }
        } else {
            throw AppException.badRequest("未知的验证码场景");
        }
        String code = emailUtil.generateCode();
        emailUtil.sendVerifyCode(email, code);
        redisTemplate.opsForValue().set(limitKey, "1", 60, TimeUnit.SECONDS);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = authMapper.selectByEmail(request.getEmail());
        if (user == null) {
            throw AppException.userNotFound();
        }
        if (request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw AppException.passwordError();
        }
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (authMapper.selectByEmail(request.getEmail()) != null) {
            throw AppException.emailExists();
        }
        if (!emailUtil.verifyCode(request.getEmail(), request.getCode())) {
            throw AppException.verifyCodeError();
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        authMapper.insert(user);   // MP 在 insert 时回填 user.id，必须先 insert

        UserSettings userSettings = new UserSettings();
        userSettings.setUserId(user.getId());
        userSettings.setFriendVerify(1);
        userSettings.setCreatedAt(LocalDateTime.now());
        userSettings.setUpdatedAt(LocalDateTime.now());
        userSettingsMapper.insert(userSettings);

        return buildAuthResponse(user);
    }

    @Override
    public void forgetPassword(ForgetPasswordRequest request) {
        User user = authMapper.selectByEmail(request.getEmail());
        if (user == null) {
            throw AppException.userNotFound();
        }
        if (!emailUtil.verifyCode(request.getEmail(), request.getCode())) {
            throw AppException.verifyCodeError();
        }
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encodedPassword);
        user.setUpdatedAt(LocalDateTime.now());
        authMapper.updateById(user);
        tokenBlacklistService.revokeUserTokens(user.getId());
    }
    
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setEmail(user.getEmail());
        userInfo.setNickname(user.getNickname());
        userInfo.setAvatar(user.getAvatar());

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUserInfo(userInfo);

        return response;
    }

    @Override
    public void logout(String authHeader, String refreshToken) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            blacklistToken(authHeader.substring(7));
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            blacklistToken(refreshToken);
        }
    }

    private void blacklistToken(String token) {
        try {
            long remaining = jwtUtil.getTokenRemainingMs(token);
            tokenBlacklistService.blacklist(token, remaining);
        } catch (Exception e) {
            log.warn("Failed to blacklist token", e);
        }
    }

    @Override
    public TokenRefreshResponse refreshAccessToken(String refreshToken) {
        if (refreshToken != null && refreshToken.length() >= 2
                && refreshToken.startsWith("\"") && refreshToken.endsWith("\"")) {
            refreshToken = refreshToken.substring(1, refreshToken.length() - 1);
        }
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            log.warn("refresh rejected: blacklisted");
            throw AppException.tokenInvalid();
        }
        try {
            Claims claims = jwtUtil.parseToken(refreshToken);
            if (!jwtUtil.isRefreshToken(claims)) {
                log.warn("refresh rejected: type={}", claims.get("type"));
                throw AppException.tokenInvalid();
            }
            if (tokenBlacklistService.isRevokedByPasswordChange(claims.getSubject(), claims.getIssuedAt())) {
                log.warn("refresh rejected: revoked by password change");
                throw AppException.tokenInvalid();
            }
            String userId = claims.getSubject();
            TokenRefreshResponse response = new TokenRefreshResponse();
            response.setAccessToken(jwtUtil.generateAccessToken(userId));
            response.setRefreshToken(jwtUtil.generateRefreshToken(userId));
            // 旧 refreshToken 立即作废。响应若丢失，客户端只能重新登录
            blacklistToken(refreshToken);
            return response;
        } catch (AppException e) {
            throw e;
        } catch (ExpiredJwtException e) {
            throw AppException.tokenExpired();
        } catch (Exception e) {
            log.warn("refresh failed: {}", e.toString());
            throw AppException.tokenInvalid();
        }
    }

}
