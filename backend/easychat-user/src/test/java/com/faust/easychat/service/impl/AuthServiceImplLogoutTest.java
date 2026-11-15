package com.faust.easychat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.faust.easychat.enums.ReturnCodeEnum;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.mapper.AuthMapper;
import com.faust.easychat.mapper.UserSettingsMapper;
import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.EmailUtil;
import com.faust.easychat.util.JwtUtil;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplLogoutTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Mock
    private AuthMapper authMapper;
    @Mock
    private UserSettingsMapper userSettingsMapper;
    @Mock
    private EmailUtil emailUtil;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    private JwtUtil jwtUtil;
    private SecretKey signingKey;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 1_800_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604_800_000L);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        authService = new AuthServiceImpl(
                authMapper,
                userSettingsMapper,
                jwtUtil,
                emailUtil,
                redisTemplate,
                tokenBlacklistService,
                passwordEncoder);
    }

    @Test
    void logout_blacklistsNormalizedAccessAndRefreshTokens() {
        String accessToken = jwtUtil.generateAccessToken("user-1");
        String refreshToken = jwtUtil.generateRefreshToken("user-1");

        authService.logout("Bearer   " + accessToken + "  ", "  \"" + refreshToken + "\"  ");

        verify(tokenBlacklistService).blacklist(eq(accessToken), longThat(ttl -> ttl > 0));
        verify(tokenBlacklistService).blacklist(eq(refreshToken), longThat(ttl -> ttl > 0));
    }

    @Test
    void logout_redisFailureOnAccessToken_propagatesDataAccessException() {
        String accessToken = jwtUtil.generateAccessToken("user-1");
        doThrow(new DataAccessResourceFailureException("redis unavailable"))
                .when(tokenBlacklistService).blacklist(eq(accessToken), anyLong());

        assertThrows(DataAccessException.class,
                () -> authService.logout("Bearer " + accessToken, null));
    }

    @Test
    void logout_redisFailureOnRefreshToken_propagatesDataAccessException() {
        String accessToken = jwtUtil.generateAccessToken("user-1");
        String refreshToken = jwtUtil.generateRefreshToken("user-1");
        doNothing().when(tokenBlacklistService).blacklist(eq(accessToken), anyLong());
        doThrow(new DataAccessResourceFailureException("redis unavailable"))
                .when(tokenBlacklistService).blacklist(eq(refreshToken), anyLong());

        assertThrows(DataAccessException.class,
                () -> authService.logout("Bearer " + accessToken, refreshToken));

        verify(tokenBlacklistService).blacklist(eq(accessToken), anyLong());
    }

    @Test
    void logout_alreadyExpiredAccessToken_skipsSafelyAndStillBlacklistsRefreshToken() {
        String expiredAccess = expiredToken("access");
        String refreshToken = jwtUtil.generateRefreshToken("user-1");

        authService.logout("Bearer " + expiredAccess, refreshToken);

        verify(tokenBlacklistService, never()).blacklist(eq(expiredAccess), anyLong());
        verify(tokenBlacklistService).blacklist(eq(refreshToken), longThat(ttl -> ttl > 0));
    }

    @Test
    void logout_alreadyExpiredRefreshToken_skipsSafelyAfterBlacklistingAccessToken() {
        String accessToken = jwtUtil.generateAccessToken("user-1");
        String expiredRefresh = expiredToken("refresh");

        authService.logout("Bearer " + accessToken, expiredRefresh);

        verify(tokenBlacklistService).blacklist(eq(accessToken), longThat(ttl -> ttl > 0));
        verify(tokenBlacklistService, never()).blacklist(eq(expiredRefresh), anyLong());
    }

    @Test
    void logout_malformedRefreshToken_throwsTokenInvalid() {
        String accessToken = jwtUtil.generateAccessToken("user-1");

        AppException ex = assertThrows(AppException.class,
                () -> authService.logout("Bearer " + accessToken, "not-a-jwt"));

        assertEquals(ReturnCodeEnum.TOKEN_INVALID, ex.getReturnCode());
        verify(tokenBlacklistService).blacklist(eq(accessToken), longThat(ttl -> ttl > 0));
    }

    @Test
    void logout_blankHeaderAndQuotedBlankRefreshToken_skipsBlacklist() {
        authService.logout("Bearer   ", "  \"   \"  ");

        verifyNoInteractions(tokenBlacklistService);
    }

    private String expiredToken(String type) {
        return Jwts.builder()
                .subject("user-1")
                .claim("type", type)
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(signingKey)
                .compact();
    }
}
