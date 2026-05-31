package com.epam.execution_engine_service.gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * SRP: Single responsibility — enforce per-key request rate limits using Redis.
 * Uses atomic INCR so concurrent requests cannot both slip through the limit
 * (eliminates the read-then-increment race condition of the old approach).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimitService implements RateLimitService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.redis.rate-limit.requests-per-minute:5}")
    private int requestsPerMinute;

    @Override
    public boolean tryConsume(String key) {
        // Atomic increment — returns the value AFTER the increment
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            log.warn("[RateLimit] Redis returned null for key {}, allowing request", key);
            return true;
        }
        // Set TTL only on the first request in the window (count == 1)
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(60));
        }
        if (count > requestsPerMinute) {
            log.warn("[RateLimit] Limit exceeded for key: {} (count={})", key, count);
            return false;
        }
        return true;
    }
}

