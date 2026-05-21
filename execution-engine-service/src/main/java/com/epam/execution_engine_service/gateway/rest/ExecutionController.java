package com.epam.execution_engine_service.gateway.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.domain.ExecutionRequest;
import com.epam.execution_engine_service.domain.ExecutionStatus;
import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
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
@Tag(name = "Executions", description = "Submit code for execution and poll results")
public class ExecutionController {

    private final KafkaTemplate<String, ExecutionTaskEvent> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.kafka.topic:execution-tasks}")
    private String kafkaTopic;

    @Value("${app.redis.status-ttl-seconds:600}")
    private long statusTtlSeconds;

    @Operation(
        summary     = "Submit a code execution",
        description = "Enqueues the submitted source code for execution against the given problem's test cases. " +
                      "Returns immediately with an `executionId` that can be used to poll status via WebSocket or GET /api/executions/{id}/status. " +
                      "**Requires a Bearer JWT** — call `GET /api/dev/token` first (dev profile only), then click the 'Authorize' button.",
        security    = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "202",
            description  = "Execution accepted and queued",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = Map.class),
                examples  = @ExampleObject(
                    name  = "Accepted",
                    value = "{\"executionId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\", \"status\": \"PENDING\"}"
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request body (validation failed)"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitExecution(
            @Valid @RequestBody ExecutionRequest request,
            Authentication authentication) {

        UUID executionId = UUID.randomUUID();
        Long userId = Long.parseLong(authentication.getName());

        String redisKey = "execution:status:" + executionId;
        redisTemplate.opsForValue().set(redisKey, ExecutionStatus.PENDING.name(),
                Duration.ofSeconds(statusTtlSeconds));

        ExecutionTaskEvent taskEvent = ExecutionTaskEvent.builder()
                .executionId(executionId)
                .userId(userId)
                .problemId(request.getProblemId())
                .language(request.getLanguage())
                .mode(request.getMode())
                .sourceCode(request.getSourceCode())
                .submittedAt(Instant.now())
                .build();

        kafkaTemplate.send(kafkaTopic, String.valueOf(userId), taskEvent);

        log.info("Submitted execution {} for user {} / problem {}", executionId, userId, request.getProblemId());

        return ResponseEntity.accepted().body(Map.of(
                "executionId", executionId.toString(),
                "status", ExecutionStatus.PENDING.name()
        ));
    }
}