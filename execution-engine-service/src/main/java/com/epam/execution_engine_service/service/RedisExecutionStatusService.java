package com.epam.execution_engine_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RedisExecutionStatusService — Execution status tracking in Redis (SRS §8, §2.2)
 * 
 * Stores execution status in Redis for fast, distributed access.
 * 
 * Key Pattern: execution:status:{executionId}
 * TTL: Configurable via app.redis.status-ttl-seconds (default: 600s = 10 minutes)
 * 
 * Status Values: PENDING, RUNNING, COMPLETED, FAILED
 * 
 * Flow (SRS Section 2.2):
 * 1. Step 4: Engine generates executionId and writes PENDING status to Redis KV
 * 2. Step 6: Returns HTTP 202 Accepted with executionId
 * 3. Step 13: Persistence updates status to COMPLETED after DB write
 * 4. Client can poll GET /api/executions/{id}/status for status (REST fallback)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisExecutionStatusService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.status-ttl-seconds:600}")
    private long statusTtlSeconds;

    /**
     * Store execution status in Redis
     * 
     * @param executionId UUID execution identifier
     * @param status Status value (PENDING, RUNNING, COMPLETED, FAILED)
     */
    public void setStatus(UUID executionId, String status) {
        String key = "execution:status:" + executionId;
        try {
            redisTemplate.opsForValue().set(
                    key,
                    status,
                    statusTtlSeconds,
                    TimeUnit.SECONDS
            );
            log.debug("Status stored in Redis: key={}, status={}, ttl={}s", key, status, statusTtlSeconds);
        } catch (Exception e) {
            log.error("Failed to store status in Redis: key={}", key, e);
        }
    }

    /**
     * Retrieve execution status from Redis
     * 
     * @param executionId UUID execution identifier
     * @return Status value or null if not found/expired
     */
    public String getStatus(UUID executionId) {
        String key = "execution:status:" + executionId;
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("Failed to retrieve status from Redis: key={}", key, e);
            return null;
        }
    }

    /**
     * Delete execution status from Redis (cleanup after TTL or on demand)
     * 
     * @param executionId UUID execution identifier
     */
    public void deleteStatus(UUID executionId) {
        String key = "execution:status:" + executionId;
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("Status deleted from Redis: key={}, deleted={}", key, deleted);
        } catch (Exception e) {
            log.error("Failed to delete status from Redis: key={}", key, e);
        }
    }

}
