package com.epam.execution_engine_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ExecutionResultEvent — Final result DTO (SRS §9)
 * 
 * Returned by ExecutionOrchestratorService after execution completes.
 * Written to Redis KV store and published via Redis Pub/Sub.
 * Delivered to client via WebSocket or REST fallback.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionResultEvent {

    /**
     * Unique execution identifier
     */
    @JsonProperty("execution_id")
    private UUID executionId;

    /**
     * User identifier
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * Problem identifier
     */
    @JsonProperty("problem_id")
    private Long problemId;

    /**
     * Human-readable problem name (e.g. "Two Sum").
     * Stored separately from problemId for display purposes.
     */
    @JsonProperty("problem_name")
    private String problemName;

    /**
     * Final verdict: COMPILE_ERROR, RUNTIME_ERROR, PASSED, PARTIAL_SUCCESS, TIME_LIMIT_EXCEEDED
     */
    @NotBlank(message = "verdict is required")
    @Pattern(
        regexp = "^(COMPILE_ERROR|RUNTIME_ERROR|PASSED|PARTIAL_SUCCESS|TIME_LIMIT_EXCEEDED)$",
        message = "verdict must be one of: COMPILE_ERROR, RUNTIME_ERROR, PASSED, PARTIAL_SUCCESS, TIME_LIMIT_EXCEEDED"
    )
    @JsonProperty("verdict")
    private String verdict;

    /**
     * Score as percentage (0.0 - 100.0)
     */
    @NotNull(message = "score is required")
    @Min(value = 0, message = "score must be >= 0")
    @Max(value = 100, message = "score must be <= 100")
    @JsonProperty("score")
    private Double score;

    /**
     * Total runtime in milliseconds (sum of all test cases)
     */
    @JsonProperty("total_runtime_ms")
    private Long totalRuntimeMs;

    /**
     * Maximum memory used in bytes
     */
    @JsonProperty("total_memory_bytes")
    private Long totalMemoryBytes;

    /**
     * Individual test case results
     */
    @JsonProperty("test_case_results")
    private List<TestCaseResultDto> testCaseResults;

    /**
     * Timestamp when execution completed
     */
    @JsonProperty("completed_at")
    private Instant completedAt;

}
