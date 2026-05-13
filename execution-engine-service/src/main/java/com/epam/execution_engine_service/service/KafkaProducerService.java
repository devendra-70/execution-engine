package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.ExecutionTaskEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * KafkaProducerService — Kafka message publishing (SRS §7, §4)
 * 
 * Publishes ExecutionTaskEvent to Kafka topic "execution-tasks".
 * Configuration:
 * - Topic: execution-tasks
 * - Key: userId (ensures per-user ordering)
 * - Acks: all (durability)
 * - Idempotent producer: true (at-least-once delivery)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic:execution-tasks}")
    private String kafkaTopic;

    /**
     * Publish execution task event to Kafka (SRS §7).
     * Message key = userId to guarantee per-user ordering (SRS §7.1).
     *
     * @param event ExecutionTaskEvent to publish
     */
    public void publishExecutionTaskEvent(ExecutionTaskEvent event) {
        try {
            // userId used as message key — guarantees per-user partition ordering (SRS §7.1)
            String messageKey = event.getUserId() != null ? event.getUserId().toString() : null;
            kafkaTemplate.send(kafkaTopic, messageKey, event);
        } catch (Exception e) {
            throw new RuntimeException("Kafka publishing failed", e);
        }
    }

}
