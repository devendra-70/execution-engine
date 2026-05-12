package com.epam.execution_engine_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TestCaseDto — Test case DTO for API responses (SRS §9)
 * 
 * Represents a single test case fetched from the database or cache:
 * - id: Test case identifier
 * - problemId: Which problem it belongs to
 * - input: Input to feed to user's code
 * - expectedOutput: Expected output
 * - timeoutMs: Timeout in milliseconds
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseDto {

    /**
     * Test case identifier
     */
    @JsonProperty("id")
    private Long id;

    /**
     * Problem identifier
     */
    @JsonProperty("problem_id")
    private Long problemId;

    /**
     * Input for this test case
     */
    @JsonProperty("input")
    private String input;

    /**
     * Expected output
     */
    @JsonProperty("expected_output")
    private String expectedOutput;

    /**
     * Timeout in milliseconds
     */
    @JsonProperty("timeout_ms")
    private Integer timeoutMs;

}
