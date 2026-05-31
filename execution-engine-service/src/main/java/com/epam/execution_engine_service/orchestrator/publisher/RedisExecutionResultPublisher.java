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
        // 1. Store a rich summary in Redis KV so the REST status endpoint exposes
        //    verdict + score + runtime — matching exactly what is pushed via WebSocket.
        String summaryJson = objectMapper.writeValueAsString(java.util.Map.of(
                "status",         ExecutionStatus.COMPLETED.name(),
                "verdict",        resultEvent.getVerdict().name(),
                "score",          resultEvent.getScore(),
                "totalRuntimeMs", resultEvent.getTotalRuntimeMs()
        ));
        String key = "execution:status:" + resultEvent.getExecutionId();
        redisTemplate.opsForValue().set(key, summaryJson, java.time.Duration.ofSeconds(statusTtlSeconds));

        // 2. Broadcast full result via Pub/Sub so WebSocket layer delivers it to clients
        String resultJson = objectMapper.writeValueAsString(resultEvent);
        redisTemplate.convertAndSend("execution-completed", resultJson);
        log.debug("[Publisher] Result published for executionId={}", resultEvent.getExecutionId());
    }
}

