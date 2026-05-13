package com.epam.execution_engine_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TestCaseResultDto — Individual test case result DTO (SRS §9)
 * 
 * Represents the result of executing one test case:
 * - testCaseId: Which test case
 * - status: PASS, FAIL, RUNTIME_ERROR, COMPILE_ERROR, TIME_LIMIT_EXCEEDED
 * - actualOutput: What the code produced
 * - expectedOutput: What was expected
 * - executionTimeMs: How long it took
 * - memoryBytes: Memory consumed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseResultDto {

    /**
     * Test case identifier
     */
    @JsonProperty("test_case_id")
    private Long testCaseId;

    /**
     * Result status
     */
    @JsonProperty("status")
    private String status;

    /**
     * Actual output produced by user's code
     */
    @JsonProperty("actual_output")
    private String actualOutput;

    /**
     * Expected output
     */
    @JsonProperty("expected_output")
    private String expectedOutput;

    /**
     * Execution time in milliseconds
     */
    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;

    /**
     * Memory used in bytes
     */
    @JsonProperty("memory_bytes")
    private Long memoryBytes;

}
