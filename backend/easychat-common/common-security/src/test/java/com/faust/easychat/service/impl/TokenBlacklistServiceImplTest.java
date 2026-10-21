package com.faust.easychat.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private TokenBlacklistServiceImpl tokenBlacklistService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenBlacklistService, "refreshExpirationMs", 604_800_000L);
    }

    @Test
    void blacklist_positiveTtl_setsRedisKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        tokenBlacklistService.blacklist("access-token", 60_000);

        verify(valueOps).set(eq("token:blacklist:access-token"), eq("1"), eq(60_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void blacklist_nonPositiveTtl_skipsRedis() {
        tokenBlacklistService.blacklist("access-token", 0);
        tokenBlacklistService.blacklist("access-token", -1);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void isBlacklisted_delegatesToRedis() {
        when(redisTemplate.hasKey("token:blacklist:access-token")).thenReturn(true);
        when(redisTemplate.hasKey("token:blacklist:other")).thenReturn(false);

        assertTrue(tokenBlacklistService.isBlacklisted("access-token"));
        assertFalse(tokenBlacklistService.isBlacklisted("other"));
    }

    @Test
    void passwordChange_rejectsTokensIssuedAtOrBeforeCutoff() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("token:revoke-before:user-1")).thenReturn("1700000000000");

        assertTrue(tokenBlacklistService.isRevokedByPasswordChange("user-1", new Date(1700000000000L)));
        assertTrue(tokenBlacklistService.isRevokedByPasswordChange("user-1", new Date(1699999999999L)));
        assertFalse(tokenBlacklistService.isRevokedByPasswordChange("user-1", new Date(1700000000001L)));
    }
}
