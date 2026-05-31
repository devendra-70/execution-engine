package com.epam.execution_engine_service.orchestrator.publisher;

import com.epam.execution_engine_service.domain.ExecutionResultEvent;
import com.epam.execution_engine_service.domain.ExecutionStatus;

import java.util.UUID;

/**
 * ISP/DIP: Abstraction for publishing execution status and results.
 * Decouples the orchestrator from Redis implementation details.
 */
public interface ExecutionResultPublisher {

    /**
     * Update the Redis KV status for an execution (e.g., PENDING, PROCESSING, COMPLETED).
     */
    void publishStatus(UUID executionId, ExecutionStatus status);

    /**
     * Persist the final result in Redis KV and broadcast via Pub/Sub for WebSocket delivery.
     *
     * @throws Exception if serialization or Redis publish fails
     */
    void publishResult(ExecutionResultEvent resultEvent) throws Exception;
}

