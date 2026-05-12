package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.gateway.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Rate Limiter Service
 * Implements token bucket algorithm in Redis with atomic operations
 * Enforces rate limiting per userId AND per clientIp
 * Uses RedisAtomicLong for atomic get-and-increment operations (SRS Section 8)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {
    
    private static final String RATE_LIMIT_USER_KEY_PREFIX = "rate-limit:user:";
    private static final String RATE_LIMIT_IP_KEY_PREFIX = "rate-limit:ip:";
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ApplicationProperties applicationProperties;
    
    /**
     * Check if request is allowed for user and IP
     * @param userId The user ID
     * @param clientIp The client IP address
     * @return true if allowed, false if rate limit exceeded
     * @throws RateLimitException if rate limit is exceeded
     */
    public void checkRateLimit(String userId, String clientIp) {
        int requestsPerMinute = applicationProperties.getRateLimit().getRequestsPerMinute();
        int windowSeconds = applicationProperties.getRateLimit().getWindowSeconds();
        
        // Check user rate limit
        if (!isAllowed(RATE_LIMIT_USER_KEY_PREFIX + userId, requestsPerMinute, windowSeconds)) {
            throw new RateLimitException(
                    "Rate limit exceeded for user: " + userId + 
                    " (max " + requestsPerMinute + " requests per minute)"
            );
        }
        
        // Check IP rate limit
        if (!isAllowed(RATE_LIMIT_IP_KEY_PREFIX + clientIp, requestsPerMinute, windowSeconds)) {
            throw new RateLimitException(
                    "Rate limit exceeded for IP: " + clientIp + 
                    " (max " + requestsPerMinute + " requests per minute)"
            );
        }
    }
    
    private boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        try {
            // Use RedisTemplate.opsForValue() for atomic operations (SRS Section 8)
            // increment() returns the new value after incrementing
            ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
            
            // Get current count (null means key doesn't exist, treat as 0)
            String countStr = valueOps.get(key);
            long count = countStr == null ? 0 : Long.parseLong(countStr);
            
            if (count == 0) {
                // This is a new key, set its expiration time
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }
            
            if (count < maxRequests) {
                // Atomic increment (increment returns new value)
                valueOps.increment(key);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            // If Redis fails, deny the request (fail closed - SRS Section 8)
            // This prevents DoS attacks when Redis becomes unavailable
            throw new RateLimitException(
                    "Rate limiting service temporarily unavailable. Please try again later.", e);
        }
    }
}