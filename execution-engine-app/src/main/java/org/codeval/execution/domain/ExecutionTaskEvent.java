package org.codeval.execution.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionTaskEvent {
    private UUID executionId;
    private Long userId;
    private Long problemId;
    private String language;
    private String mode;
    private String sourceCode;
    private Instant submittedAt;
}

