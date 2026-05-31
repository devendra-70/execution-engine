package com.epam.execution_engine_service.orchestrator.kafka;

import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.domain.ExecutionStatus;
import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import com.epam.execution_engine_service.orchestrator.ExecutionOrchestrationService;
import com.epam.execution_engine_service.orchestrator.publisher.ExecutionResultPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * DIP: Uses {@link ExecutionResultPublisher} for status updates instead of StringRedisTemplate directly.
 */
@Slf4j
@Component
public class ExecutionTaskListener {

    private final ExecutionOrchestrationService orchestrationService;
    private final ExecutionResultPublisher resultPublisher;
    private final ThreadPoolTaskExecutor orchestrationPool;

    public ExecutionTaskListener(
            ExecutionOrchestrationService orchestrationService,
            ExecutionResultPublisher resultPublisher,
            @Qualifier("orchestrationPool") ThreadPoolTaskExecutor orchestrationPool) {
        this.orchestrationService = orchestrationService;
        this.resultPublisher      = resultPublisher;
        this.orchestrationPool    = orchestrationPool;
    }

    @KafkaListener(
            topics = "${app.kafka.topic:execution-tasks}",
            groupId = "execution-engine-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onExecutionTask(@Payload ExecutionTaskEvent event, Acknowledgment ack) {
        log.info("Received task from Kafka: executionId={}", event.getExecutionId());

        // Update status to PROCESSING via publisher abstraction (DIP)
        resultPublisher.publishStatus(event.getExecutionId(), ExecutionStatus.PROCESSING);

        // Hand off to orchestration pool — ack ONLY after DB commit succeeds
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
