package com.epam.execution_engine_service.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the execution status stored in Redis.
 * Key format: execution:status:{executionId}
 * TTL: Configurable via app.redis.status-ttl-seconds (default 600 seconds)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionStatus {
    private String executionId;
    private ExecutionStatusEnum status;
    private long submittedAtMs;
    private String userId;
    private String problemId;
    private String language;
    private String mode; // RUN or SUBMIT

    /**
     * Returns human-readable submission time
     */
    public String getSubmittedAtIso8601() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .format(new java.util.Date(submittedAtMs));
    }
}
