package org.codeval.execution.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResultEvent {
    private UUID executionId;
    private Long userId;
    private Long problemId;
    private String problemName;
    private Verdict verdict;
    private int score;
    private long totalRuntimeMs;
    private long memoryBytes;
    private List<TestCaseResultEvent> testCaseResults;
}

