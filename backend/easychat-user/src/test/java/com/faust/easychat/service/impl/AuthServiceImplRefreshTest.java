package com.faust.easychat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.faust.easychat.dto.user.TokenRefreshResponse;
import com.faust.easychat.enums.ReturnCodeEnum;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.mapper.AuthMapper;
import com.faust.easychat.mapper.UserSettingsMapper;
import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.EmailUtil;
import com.faust.easychat.util.JwtUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplRefreshTest {

    @Mock
    private AuthMapper authMapper;
    @Mock
    private UserSettingsMapper userSettingsMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private EmailUtil emailUtil;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private Claims claims;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void refresh_rotatesRefreshTokenAndBlacklistsTheOldOne() {
        when(tokenBlacklistService.isBlacklisted("old-refresh")).thenReturn(false);
        when(jwtUtil.parseToken("old-refresh")).thenReturn(claims);
        when(jwtUtil.isRefreshToken(claims)).thenReturn(true);
        when(claims.getSubject()).thenReturn("user-1");
        when(claims.getIssuedAt()).thenReturn(new Date());
        when(tokenBlacklistService.isRevokedByPasswordChange(org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(jwtUtil.generateAccessToken("user-1")).thenReturn("access-2");
        when(jwtUtil.generateRefreshToken("user-1")).thenReturn("refresh-2");
        when(jwtUtil.getTokenRemainingMs("old-refresh")).thenReturn(5_000L);

        TokenRefreshResponse response = authService.refreshAccessToken("old-refresh");

        assertEquals("access-2", response.getAccessToken());
        assertEquals("refresh-2", response.getRefreshToken());
        verify(tokenBlacklistService).blacklist("old-refresh", 5_000L);
    }

    @Test
    void refresh_normalizesWhitespaceAndQuotedRefreshToken() {
        when(tokenBlacklistService.isBlacklisted("old-refresh")).thenReturn(false);
        when(jwtUtil.parseToken("old-refresh")).thenReturn(claims);
        when(jwtUtil.isRefreshToken(claims)).thenReturn(true);
        when(claims.getSubject()).thenReturn("user-1");
        when(claims.getIssuedAt()).thenReturn(new Date());
        when(tokenBlacklistService.isRevokedByPasswordChange(eq("user-1"), any())).thenReturn(false);
        when(jwtUtil.generateAccessToken("user-1")).thenReturn("access-2");
        when(jwtUtil.generateRefreshToken("user-1")).thenReturn("refresh-2");
        when(jwtUtil.getTokenRemainingMs("old-refresh")).thenReturn(5_000L);

        TokenRefreshResponse response = authService.refreshAccessToken("  \" old-refresh \"  ");

        assertEquals("access-2", response.getAccessToken());
        verify(tokenBlacklistService).isBlacklisted("old-refresh");
        verify(tokenBlacklistService).blacklist("old-refresh", 5_000L);
    }

    @Test
    void refresh_blankOrQuotedEmptyToken_throwsTokenInvalidWithoutCallingRedis() {
        for (String raw : new String[] { null, "", "   ", "\"\"", " \"  \" " }) {
            AppException ex = assertThrows(AppException.class, () -> authService.refreshAccessToken(raw));
            assertEquals(ReturnCodeEnum.TOKEN_INVALID, ex.getReturnCode());
        }
        verifyNoInteractions(tokenBlacklistService);
    }

    @Test
    void refresh_redisFailureOnBlacklistDuringRotation_propagatesDataAccessException() {
        when(tokenBlacklistService.isBlacklisted("old-refresh")).thenReturn(false);
        when(jwtUtil.parseToken("old-refresh")).thenReturn(claims);
        when(jwtUtil.isRefreshToken(claims)).thenReturn(true);
        when(claims.getSubject()).thenReturn("user-1");
        when(claims.getIssuedAt()).thenReturn(new Date());
        when(tokenBlacklistService.isRevokedByPasswordChange(eq("user-1"), any())).thenReturn(false);
        when(jwtUtil.generateAccessToken("user-1")).thenReturn("access-2");
        when(jwtUtil.generateRefreshToken("user-1")).thenReturn("refresh-2");
        when(jwtUtil.getTokenRemainingMs("old-refresh")).thenReturn(5_000L);
        doThrow(new DataAccessResourceFailureException("redis down"))
                .when(tokenBlacklistService).blacklist("old-refresh", 5_000L);

        assertThrows(DataAccessException.class, () -> authService.refreshAccessToken("old-refresh"));
    }

    @Test
    void refresh_redisFailureOnRevokedCheck_propagatesDataAccessException() {
        when(tokenBlacklistService.isBlacklisted("old-refresh")).thenReturn(false);
        when(jwtUtil.parseToken("old-refresh")).thenReturn(claims);
        when(jwtUtil.isRefreshToken(claims)).thenReturn(true);
        when(claims.getSubject()).thenReturn("user-1");
        when(claims.getIssuedAt()).thenReturn(new Date());
        when(tokenBlacklistService.isRevokedByPasswordChange(eq("user-1"), any()))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        assertThrows(DataAccessException.class, () -> authService.refreshAccessToken("old-refresh"));
    }

    @Test
    void refresh_expiredToken_throwsTokenExpired() {
        when(tokenBlacklistService.isBlacklisted("expired-refresh")).thenReturn(false);
        when(jwtUtil.parseToken("expired-refresh")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        AppException ex = assertThrows(AppException.class, () -> authService.refreshAccessToken("expired-refresh"));
        assertEquals(ReturnCodeEnum.TOKEN_EXPIRED, ex.getReturnCode());
    }
}
