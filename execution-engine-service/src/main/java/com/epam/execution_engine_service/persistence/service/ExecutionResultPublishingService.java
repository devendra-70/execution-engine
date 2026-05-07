package com.epam.execution_engine_service.persistence.service;

import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.mapper.ResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for publishing execution results to Redis KV and Pub/Sub.
 * Implements SRS §8 Redis design patterns for caching and real-time notifications.
 *
 * Non-transactional publish to Redis after database persistence.
 * Redis publishes are best-effort only - failures do not trigger rollback.
 * If Redis is unavailable, the persistence operation succeeds anyway (SRS §8).
 *
 * Non-blocking operations: failures logged but do NOT propagate to caller.
 * Thread-safe: RedisTemplate is thread-safe; ObjectMapper is stateless.
 *
 * Redis Patterns:
 * - KV: execution:status:{executionId} = ExecutionResultEvent JSON (TTL: 600s)
 * - Pub/Sub: channel "execution-completed" = ExecutionResultEvent JSON
 */
@Slf4j
@Service
public class ExecutionResultPublishingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ResultMapper resultMapper;
    private final ObjectMapper objectMapper;
    private final Long statusTtlSeconds;

    /**
     * Constructor with explicit dependency injection.
     * statusTtlSeconds defaults to 600 seconds if not configured.
     * @param redisTemplate Redis template for KV and Pub/Sub operations
     * @param resultMapper Mapper for entity-to-event conversion
     * @param objectMapper Jackson mapper for JSON serialization
     * @param statusTtlSeconds TTL in seconds for Redis execution status cache (from app.redis.status-ttl-seconds property)
     */
    public ExecutionResultPublishingService(
            RedisTemplate<String, String> redisTemplate,
            ResultMapper resultMapper,
            ObjectMapper objectMapper,
            @Value("${app.redis.status-ttl-seconds:600}") Long statusTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.resultMapper = resultMapper;
        this.objectMapper = objectMapper;
        // Null-safe: use default 600 if not injected (e.g., in unit tests without Spring context)
        this.statusTtlSeconds = statusTtlSeconds != null ? statusTtlSeconds : 600L;
    }

    /**
     * Redis key pattern for execution status caching.
     * Used by: publishExecutionResult(), getExecutionResult(), clearExecutionResult()
     */
    private static final String REDIS_STATUS_KEY_PATTERN = "execution:status:%s";

    /**
     * Generates Redis cache key for execution status.
     * Centralizes key pattern logic for maintainability.
     *
     * @param executionId UUID of the execution
     * @return Redis key string
     */
    private String getStatusKey(java.util.UUID executionId) {
        return String.format(REDIS_STATUS_KEY_PATTERN, executionId);
    }

    /**
     * Publishes execution result to Redis cache and pub/sub (SRS §8, EPMICMPCOD-462).
     * Called after database persistence succeeds (SRS §5.2 step 13–14).
     *
     * Operations:
     * 1. Serialize SubmissionEntity to JSON
     * 2. Write to KV: execution:status:{executionId} with TTL (app.redis.status-ttl-seconds)
     * 3. Publish to Pub/Sub channel: "execution-completed"
     *
     * Non-blocking: Redis timeouts do NOT propagate; failures are logged only.
     *
     * @param entity SubmissionEntity from database (persisted)
     * @throws NullPointerException if entity or executionId is null
     */
    public void publishExecutionResult(SubmissionEntity entity) {
        if (entity == null || entity.getExecutionId() == null) {
            throw new IllegalArgumentException("SubmissionEntity and executionId cannot be null");
        }

        try {
            // Convert entity back to DTO for serialization (clean contract)
            final ExecutionResultEvent event = resultMapper.toExecutionResultEvent(entity);

            // Serialize to JSON
            final String jsonPayload = objectMapper.writeValueAsString(event);

            // 1. Write to KV cache with TTL
            final String cacheKey = getStatusKey(entity.getExecutionId());
            redisTemplate.opsForValue().set(
                cacheKey,
                jsonPayload,
                statusTtlSeconds,
                TimeUnit.SECONDS
            );

            log.debug("Published execution result to Redis KV cache: {} (TTL: {}s)",
                cacheKey, statusTtlSeconds);

            // 2. Publish to Pub/Sub channel
            redisTemplate.convertAndSend("execution-completed", jsonPayload);

            log.debug("Published execution result to Redis Pub/Sub channel: execution-completed");

        } catch (final Exception e) {
            // Non-blocking: log but do NOT propagate (SRS §8)
            log.warn("Failed to publish execution result to Redis for executionId: {}",
                entity.getExecutionId(), e);
        }
    }

    /**
     * Retrieves a cached execution result from Redis KV.
     * Returns null if not found or expired (fallback to database query required).
     *
     * @param executionId UUID of the execution
     * @return ExecutionResultEvent if found and valid JSON, null otherwise
     */
    public ExecutionResultEvent getExecutionResult(java.util.UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId cannot be null");
        }

        try {
            final String cacheKey = getStatusKey(executionId);
            final String jsonPayload = redisTemplate.opsForValue().get(cacheKey);

            if (jsonPayload == null) {
                log.debug("Cache miss for executionId: {}", executionId);
                return null;
            }

            // Deserialize JSON to DTO
            final ExecutionResultEvent event = objectMapper.readValue(jsonPayload, ExecutionResultEvent.class);

            log.debug("Cache hit for executionId: {}", executionId);
            return event;

        } catch (final Exception e) {
            log.warn("Failed to retrieve execution result from Redis for executionId: {}",
                executionId, e);
            return null;  // Return null for fallback to database
        }
    }

    /**
     * Clears a cached execution result from Redis.
     * Called on cleanup or cache invalidation.
     *
     * @param executionId UUID of the execution
     */
    public void clearExecutionResult(java.util.UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId cannot be null");
        }

        try {
            final String cacheKey = getStatusKey(executionId);
            final Boolean deleted = redisTemplate.delete(cacheKey);

            log.debug("Cleared execution result from Redis cache for executionId: {} (deleted: {})",
                executionId, deleted);

        } catch (final Exception e) {
            log.warn("Failed to clear execution result from Redis for executionId: {}",
                executionId, e);
        }
    }
}
