package com.epam.execution_engine_service.orchestrator.publisher;

import com.epam.execution_engine_service.domain.ExecutionResultEvent;
import com.epam.execution_engine_service.domain.ExecutionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * SRP: Single responsibility — publish execution status and results to Redis.
 * Handles both the KV status store and the Pub/Sub channel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisExecutionResultPublisher implements ExecutionResultPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.status-ttl-seconds:600}")
    private long statusTtlSeconds;

    @Override
    public void publishStatus(UUID executionId, ExecutionStatus status) {
        String key = "execution:status:" + executionId;
        redisTemplate.opsForValue().set(key, status.name(), Duration.ofSeconds(statusTtlSeconds));
        log.debug("[Publisher] Status {} set for executionId={}", status, executionId);
    }

    @Override
    public void publishResult(ExecutionResultEvent resultEvent) throws Exception {
        // 1. Update Redis KV to COMPLETED
        publishStatus(resultEvent.getExecutionId(), ExecutionStatus.COMPLETED);

        // 2. Broadcast via Pub/Sub so WebSocket layer delivers the full result
        String resultJson = objectMapper.writeValueAsString(resultEvent);
        redisTemplate.convertAndSend("execution-completed", resultJson);
        log.debug("[Publisher] Result published for executionId={}", resultEvent.getExecutionId());
    }
}

