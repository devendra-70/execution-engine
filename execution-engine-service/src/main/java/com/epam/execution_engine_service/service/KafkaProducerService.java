package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.ExecutionTaskEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
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

    private final KafkaTemplate<String, ExecutionTaskEvent> kafkaTemplate;

    @Value("${app.kafka.topic:execution-tasks}")
    private String kafkaTopic;

    /**
     * Publish execution task event to Kafka (SRS §7)
     * 
     * @param event ExecutionTaskEvent to publish
     */
    public void publishExecutionTaskEvent(ExecutionTaskEvent event) {
        try {
            // Create message with userId as key (for partitioning by user)
            String messageKey = event.getUserId().toString();

            Message<ExecutionTaskEvent> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaders.TOPIC, kafkaTopic)
                    .build();

            // Send to Kafka
            kafkaTemplate.send(kafkaTopic, messageKey, event);
        } catch (Exception e) {
            throw new RuntimeException("Kafka publishing failed", e);
        }
    }

}
