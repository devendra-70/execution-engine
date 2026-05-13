package com.epam.execution_engine_service.persistence.event;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object representing a complete execution result event.
 * Input contract from Kafka ExecutionResultTopic (SRS §2.2 steps 11–12).
 *
 * Immutable structure (via Lombok @Data) enforcing functional-style code (Java 21).
 * All fields are non-null unless explicitly marked as nullable (per SRS §9).
 *
 * Used by:
 * - ExecutionResultPersistenceService.persistExecutionResult()
 * - ExecutionResultPublishingService.publishExecutionCompletion()
 * - Orchestrator Kafka listener
 *
 * @author Architecture Design Agent
 * @version 1.0
 * @since 2026-05-07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionResultEvent {

    /**
     * Unique execution identifier (idempotency key per SRS §5.2).
     * Ensures no duplicate persistence for same execution.
     */
    private UUID executionId;

    /**
     * User who submitted the code.
     */
    private Long userId;

    /**
     * Problem being solved.
     */
    private Long problemId;

    /**
     * Human-readable problem name (e.g. "Two Sum").
     * Stored separately from problemId for display purposes.
     */
    private String problemName;

    /**
     * Programming language of submitted code.
     */
    private String language;

    /**
     * Execution mode: RUN or SUBMIT (per SRS §2.2).
     */
    private String mode;

    /**
     * Final verdict: PASSED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILE_ERROR.
     */
    private String verdict;

    /**
     * Execution status: COMPLETED, FAILED, TIMEOUT.
     */
    private String status;

    /**
     * Score (nullable, awarded based on test case pass rate).
     */
    private Double score;

    /**
     * Total runtime in milliseconds.
     */
    private Long totalRuntimeMs;

    /**
     * Total memory used in bytes.
     */
    private Long memoryBytes;

    /**
     * Raw output from program execution.
     */
    private String rawOutput;

    /**
     * Error output or compilation diagnostics.
     */
    private String errorOutput;

    /**
     * Source code submitted by user.
     */
    private String submittedCode;

    /**
     * Timestamp when code was submitted to system.
     */
    private OffsetDateTime submittedAt;

    /**
     * Timestamp when execution completed.
     */
    private OffsetDateTime completedAt;

    /**
     * List of individual test case results (SRS §2.2 step 13).
     */
    private List<TestCaseResultEvent> testResults;
}
