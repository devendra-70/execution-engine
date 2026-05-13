package com.epam.execution_engine_service.persistence.event;

import lombok.*;
import java.util.UUID;

/**
 * Data Transfer Object representing a single test case execution result.
 * Part of ExecutionResultEvent.testResults list (SRS §2.2 step 12).
 *
 * Immutable structure (via Lombok @Data) supporting functional-style processing (Java 21).
 * All fields are non-null unless explicitly marked as nullable.
 *
 * Used by:
 * - ResultMapper.mapTestResults() for entity mapping
 * - ExecutionResultPersistenceService for batch persistence
 * - Redis publishing payload
 *
 * @author Architecture Design Agent
 * @version 1.0
 * @since 2026-05-07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseResultEvent {

    /**
     * Unique identifier for this test case.
     */
    private String testCaseId;

    /**
     * Status of test case execution: PASSED, FAILED, TIMEOUT, etc.
     */
    private String status;

    /**
     * Runtime for this test case in milliseconds.
     */
    private Long runtimeMs;

    /**
     * Memory used for this test case in bytes.
     */
    private Long memoryBytes;

    /**
     * Expected output (from test case definition).
     */
    private String expectedOutput;

    /**
     * Actual output produced by submitted code.
     */
    private String actualOutput;

    /**
     * Error message or diagnostics if execution failed.
     */
    private String errorOutput;
}
