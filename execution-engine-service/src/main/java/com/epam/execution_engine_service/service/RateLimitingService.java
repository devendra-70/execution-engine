package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * RateLimitingService — Redis token-bucket rate limiting (SRS §3.3)
 * 
 * Enforces global rate limit: 5 requests per minute per (userId, IP).
 * Uses Redis sorted set for rolling window implementation.
 * 
 * Only instantiated in non-test environments (enabled when app.redis.enabled != false).
 * In test contexts (@ActiveProfiles("test")), this service is mocked via @MockBean.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitingService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.redis.rate-limit.requests-per-minute:5}")
    private int requestsPerMinute;

    /**
     * Check if rate limit is exceeded and consume one token
     * 
     * @param userId User identifier
     * @param clientIp Client IP address
     * @return true if request is allowed, false if rate limit exceeded
     * @throws RateLimitExceededException if rate limit exceeded
     */
    public boolean checkAndConsume(Long userId, String clientIp) {
        String key = String.format("ratelimit:%d:%s", userId, clientIp);
        long now = System.currentTimeMillis();
        long windowStart = now - 60000; // 60 seconds ago

        try {
            // Get current count in window
            Long count = redisTemplate.boundZSetOps(key)
                    .count(windowStart, now);

            if (count != null && count >= requestsPerMinute) {
                log.warn("Rate limit exceeded for userId={}, ip={}, count={}", userId, clientIp, count);
                throw new RateLimitExceededException(
                        String.format("Rate limit exceeded: %d requests per minute", requestsPerMinute));
            }

            // Add current request
            redisTemplate.boundZSetOps(key).add(now, now);

            // Set expiration
            redisTemplate.expire(key, java.time.Duration.ofSeconds(60));

            log.debug("Rate limit check passed: userId={}, ip={}, count={}", userId, clientIp, count != null ? count + 1 : 1);
            return true;
        } catch (Exception e) {
            log.error("Error checking rate limit for userId={}, ip={}", userId, clientIp, e);
            return true; // Allow request on Redis error (fail-open)
        }
    }

}
