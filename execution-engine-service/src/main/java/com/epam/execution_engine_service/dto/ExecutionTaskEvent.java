package com.epam.execution_engine_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * ExecutionTaskEvent — Kafka message DTO (SRS §7)
 * 
 * Published to Kafka topic "execution-tasks" by the API Gateway component.
 * Contains all information needed by the Execution Orchestrator to execute the submission.
 * Message key: userId (ensures per-user ordering)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionTaskEvent {

    /**
     * Unique execution identifier (UUID)
     */
    @NotNull(message = "executionId is required")
    @JsonProperty("execution_id")
    private UUID executionId;

    /**
     * User identifier (from JWT)
     */
    @NotNull(message = "userId is required")
    @JsonProperty("user_id")
    private Long userId;

    /**
     * Problem identifier
     */
    @NotNull(message = "problemId is required")
    @JsonProperty("problem_id")
    private Long problemId;

    /**
     * Programming language/runtime (e.g., "java21", "python3")
     */
    @NotBlank(message = "language is required")
    @JsonProperty("language")
    private String language;

    /**
     * Execution mode: RUN or SUBMIT
     */
    @NotBlank(message = "mode is required")
    @JsonProperty("mode")
    private String mode;

    /**
     * Source code to execute
     */
    @NotBlank(message = "sourceCode is required")
    @Size(min = 1, max = 1_048_576, message = "Source code must be between 1 and 1,048,576 bytes (max 1 MB)")
    @JsonProperty("source_code")
    private String sourceCode;

    /**
     * Timestamp when submission was received
     */
    @NotNull(message = "submittedAt is required")
    @JsonProperty("submitted_at")
    private Instant submittedAt;

}
