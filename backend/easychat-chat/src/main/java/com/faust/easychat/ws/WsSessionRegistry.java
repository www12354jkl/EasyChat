package com.faust.easychat.ws;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class WsSessionRegistry {

    private static final String KEY_PREFIX = "ws:online:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(5);

    private static final DefaultRedisScript<Long> OFFLINE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            else
              return 0
            end
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;

    public WsSessionRegistry(
            StringRedisTemplate redisTemplate,
            @Qualifier("wsInstanceId") String instanceId) {
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceId;
    }

    public void markOnline(String userId) {
        redisTemplate.opsForValue().set(key(userId), instanceId, SESSION_TTL);
    }

    public void refresh(String userId) {
        String redisKey = key(userId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            redisTemplate.expire(redisKey, SESSION_TTL);
        }
    }

    public void markOffline(String userId) {
        redisTemplate.execute(OFFLINE_SCRIPT, List.of(key(userId)), instanceId);
    }

    public boolean isOnline(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId)));
    }

    private static String key(String userId) {
        return KEY_PREFIX + userId;
    }
}
