package org.codeval.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseResult {
    private Long testCaseId;
    private String verdict;   // ACCEPTED, WRONG_ANSWER, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED
    private String actualOutput;
    private String expectedOutput;
    private long runtimeMs;
    private long memoryBytes;
    private String errorMessage;
}

