package com.faust.easychat.aspect;

import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.faust.easychat.annotation.RateLimit;
import com.faust.easychat.exception.AppException;

import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ratelimit.enabled", havingValue = "true")
public class RateLimitAspect {
    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {

        String identifier = extractIdentifier(joinPoint, rateLimit);

        // 无标识时不参与限流，直接放行（限流是保护，不是业务规则）
        if (identifier != null) {
            String redisKey = "rate:" + joinPoint.getSignature().getName() + ":" + identifier;

            Long count = redisTemplate.opsForValue().increment(redisKey);

            if (count == 1) {
                redisTemplate.expire(redisKey, rateLimit.seconds(), TimeUnit.SECONDS);
            }

            if (count > rateLimit.count()) {
                throw AppException.tooManyRequests("请求过多,请稍后重试");
            }
        }

        return joinPoint.proceed();
    }

    /**
     * 提取限流标识：
     * 1. key() 非空 → 固定桶（所有请求共享，适合登录/注册全局防爆破）
     * 2. 否则取第一个非空 String 参数（适合 @RequestParam String email 场景）
     * 3. 都没有 → null（放行）
     */
    private String extractIdentifier(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (!rateLimit.key().isBlank()) {
            return rateLimit.key();
        }
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
