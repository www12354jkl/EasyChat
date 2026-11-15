package com.faust.easychat.ws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class WsSessionRegistryTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private WsSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WsSessionRegistry(redisTemplate, "instance-1");
    }

    @Test
    void markOnline_setsRedisKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        registry.markOnline("user-1");

        verify(valueOps).set(eq("ws:online:user-1"), eq("instance-1"), eq(java.time.Duration.ofMinutes(5)));
    }

    @Test
    void isOnline_delegatesToRedis() {
        when(redisTemplate.hasKey("ws:online:user-1")).thenReturn(true);
        when(redisTemplate.hasKey("ws:online:user-2")).thenReturn(false);

        assertTrue(registry.isOnline("user-1"));
        assertFalse(registry.isOnline("user-2"));
    }
}
