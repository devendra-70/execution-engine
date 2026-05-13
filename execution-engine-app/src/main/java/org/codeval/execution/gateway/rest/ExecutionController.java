package org.codeval.execution.gateway.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeval.execution.domain.ExecutionRequest;
import org.codeval.execution.domain.ExecutionStatus;
import org.codeval.execution.domain.ExecutionTaskEvent;
import org.codeval.execution.gateway.security.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final KafkaTemplate<String, ExecutionTaskEvent> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.kafka.topic:execution-tasks}")
    private String kafkaTopic;

    @Value("${app.redis.status-ttl-seconds:600}")
    private long statusTtlSeconds;

    @PostMapping
    public ResponseEntity<Map<String, Object>> submitExecution(
            @Valid @RequestBody ExecutionRequest request,
            Authentication authentication) {

        UUID executionId = UUID.randomUUID();
        Long userId = Long.parseLong(authentication.getName());

        // Write PENDING status to Redis
        String redisKey = "execution:status:" + executionId;
        redisTemplate.opsForValue().set(redisKey, ExecutionStatus.PENDING.name(),
                Duration.ofSeconds(statusTtlSeconds));

        // Build Kafka event
        ExecutionTaskEvent taskEvent = ExecutionTaskEvent.builder()
                .executionId(executionId)
                .userId(userId)
                .problemId(request.getProblemId())
                .language(request.getLanguage())
                .mode(request.getMode())
                .sourceCode(request.getSourceCode())
                .submittedAt(Instant.now())
                .build();

        // Publish to Kafka with userId as key (ordered per user)
        kafkaTemplate.send(kafkaTopic, String.valueOf(userId), taskEvent);

        log.info("Submitted execution {} for user {} / problem {}", executionId, userId, request.getProblemId());

        return ResponseEntity.accepted().body(Map.of(
                "executionId", executionId.toString(),
                "status", ExecutionStatus.PENDING.name()
        ));
    }
}

