package com.epam.execution_engine_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for successful POST /api/executions submission (202 Accepted).
 *
 * <p>Contains the execution ID and initial status. Full execution result is
 * delivered via WebSocket or retrieved via REST fallback endpoint.
 *
 * @author Execution Engine Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionSubmissionResponse {

    /**
     * Unique identifier for this execution job.
     * Used to track status via WebSocket or REST polling.
     */
    private UUID executionId;

    /**
     * Initial status: always PENDING at submission time.
     */
    private String status = "PENDING";

    /**
     * Server-side timestamp of the submission.
     */
    private Instant submittedAt;
}
