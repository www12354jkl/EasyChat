package com.faust.easychat.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private TokenBlacklistServiceImpl tokenBlacklistService;

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
}
