package com.epam.execution_engine_service.orchestrator.kafka;

import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.domain.ExecutionStatus;
import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import com.epam.execution_engine_service.orchestrator.ExecutionOrchestrationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class ExecutionTaskListener {

    private final ExecutionOrchestrationService orchestrationService;
    private final StringRedisTemplate redisTemplate;
    private final ThreadPoolTaskExecutor orchestrationPool;

    @Value("${app.redis.status-ttl-seconds:600}")
    private long statusTtlSeconds;

    public ExecutionTaskListener(
            ExecutionOrchestrationService orchestrationService,
            StringRedisTemplate redisTemplate,
            @Qualifier("orchestrationPool") ThreadPoolTaskExecutor orchestrationPool) {
        this.orchestrationService = orchestrationService;
        this.redisTemplate = redisTemplate;
        this.orchestrationPool = orchestrationPool;
    }

    @KafkaListener(
            topics = "${app.kafka.topic:execution-tasks}",
            groupId = "execution-engine-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onExecutionTask(@Payload ExecutionTaskEvent event, Acknowledgment ack) {
        log.info("Received task from Kafka: executionId={}", event.getExecutionId());

        // Update status to PROCESSING — keep same TTL to prevent immortal keys
        String redisKey = "execution:status:" + event.getExecutionId();
        redisTemplate.opsForValue().set(redisKey, ExecutionStatus.PROCESSING.name(),
                Duration.ofSeconds(statusTtlSeconds));

        // Hand off to Pool B — ack ONLY after DB commit succeeds
        orchestrationPool.submit(() -> {
            try {
                orchestrationService.orchestrate(event);
                ack.acknowledge();
                log.info("Kafka offset committed for executionId={}", event.getExecutionId());
            } catch (Exception e) {
                log.error("Orchestration failed for executionId={}, offset NOT committed",
                        event.getExecutionId(), e);
                // Do NOT ack — Kafka will redeliver
            }
        });
    }
}

