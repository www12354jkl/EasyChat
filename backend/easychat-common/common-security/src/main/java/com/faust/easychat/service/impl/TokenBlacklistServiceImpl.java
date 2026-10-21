package com.faust.easychat.service.impl;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.faust.easychat.service.TokenBlacklistService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String KEY_PREFIX = "token:blacklist:";
    private static final String REVOKE_PREFIX = "token:revoke-before:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    @Override
    public void blacklist(String token, long remainingMs) {
        if (remainingMs <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", remainingMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }

    @Override
    public void revokeUserTokens(String userId) {
        if (userId == null || userId.isBlank() || refreshExpirationMs <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(
                REVOKE_PREFIX + userId,
                String.valueOf(System.currentTimeMillis()),
                refreshExpirationMs,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isRevokedByPasswordChange(String userId, Date issuedAt) {
        if (userId == null || userId.isBlank() || issuedAt == null) {
            return false;
        }
        String raw = redisTemplate.opsForValue().get(REVOKE_PREFIX + userId);
        if (raw == null) {
            return false;
        }
        try {
            return issuedAt.getTime() <= Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
