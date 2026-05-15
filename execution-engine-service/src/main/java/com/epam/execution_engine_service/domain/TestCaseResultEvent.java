package com.epam.execution_engine_service.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseResultEvent {
    private Long testCaseId;
    private Verdict verdict;
    private String actualOutput;
    private String expectedOutput;
    private long runtimeMs;
    private long memoryBytes;
    private String errorMessage;
}

