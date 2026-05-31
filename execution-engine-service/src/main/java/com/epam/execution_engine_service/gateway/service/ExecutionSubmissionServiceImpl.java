package com.epam.execution_engine_service.gateway.service;

import com.epam.execution_engine_service.domain.ExecutionRequest;
import com.epam.execution_engine_service.domain.ExecutionStatus;
import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import com.epam.execution_engine_service.orchestrator.publisher.ExecutionResultPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * SRP: Owns the submission flow — status initialisation + Kafka publish.
 * The REST controller is free of infrastructure concerns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionSubmissionServiceImpl implements ExecutionSubmissionService {

    private final KafkaTemplate<String, ExecutionTaskEvent> kafkaTemplate;
    private final ExecutionResultPublisher resultPublisher;

    @Value("${app.kafka.topic:execution-tasks}")
    private String kafkaTopic;

    @Override
    public UUID submit(ExecutionRequest request, Long userId) {
        UUID executionId = UUID.randomUUID();

        // Write PENDING status to Redis via the publisher abstraction
        resultPublisher.publishStatus(executionId, ExecutionStatus.PENDING);

        // Build and publish Kafka event; use userId as key for per-user ordering
        ExecutionTaskEvent taskEvent = ExecutionTaskEvent.builder()
                .executionId(executionId)
                .userId(userId)
                .problemId(request.getProblemId())
                .language(request.getLanguage())
                .mode(request.getMode())
                .sourceCode(request.getSourceCode())
                .submittedAt(Instant.now())
                .build();

        kafkaTemplate.send(kafkaTopic, String.valueOf(userId), taskEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish execution {} to Kafka topic {}: {}",
                                executionId, kafkaTopic, ex.getMessage(), ex);
                    } else {
                        log.debug("Execution {} published to Kafka partition {} offset {}",
                                executionId,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });

        log.info("Submitted execution {} for user {} / problem {}", executionId, userId, request.getProblemId());
        return executionId;
    }
}


