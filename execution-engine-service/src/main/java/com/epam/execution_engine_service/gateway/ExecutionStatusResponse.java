package com.epam.execution_engine_service.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for GET /api/executions/{executionId}/status endpoint (SRS §3.2)
 * 
 * Provides execution status as REST fallback when WebSocket is unavailable
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionStatusResponse {
    
    /**
     * Execution ID (UUID)
     */
    private String executionId;
    
    /**
     * Current execution status:
     * - PENDING: Waiting in queue or for processing
     * - EXECUTING: Currently executing in sandbox
     * - COMPLETED: Execution finished (success or failure)
     * - FAILED: Execution encountered a critical error
     * - TIMEOUT: Execution timed out
     */
    private String status;
}
