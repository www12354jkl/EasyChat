package com.faust.easychat.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WsTicketService {

    public static final String KEY_PREFIX = "ws:ticket:";

    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public String issue(String userId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, userId, TTL);
        return ticket;
    }

    /** 取出并删除。票据只能用一次 */
    public String consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        return redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket.trim());
    }
}
