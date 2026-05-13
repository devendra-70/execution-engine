package com.epam.execution_engine_service.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ExecutionTaskEvent DTO
 * Published to Kafka topic "execution-tasks" for async task processing
 * Partitioning key: userId
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionTaskEvent {
    private String executionId;
    private String userId;
    private String problemId;
    private String language;
    private String mode; // RUN or SUBMIT
    private String sourceCode;
    private long submittedAtMs;
}
