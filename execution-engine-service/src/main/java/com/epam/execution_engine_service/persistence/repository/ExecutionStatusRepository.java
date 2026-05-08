package com.epam.execution_engine_service.persistence.repository;

import com.epam.execution_engine_service.persistence.entity.ExecutionStatus;

/**
 * Interface for ExecutionStatus repository operations
 * Implementation uses Redis as the backing store
 */
public interface ExecutionStatusRepository {
    
    /**
     * Save execution status to Redis
     * Key format: execution:status:{executionId}
     * TTL is applied based on app.redis.status-ttl-seconds
     * @param status The ExecutionStatus object
     * @return The saved ExecutionStatus
     */
    ExecutionStatus save(ExecutionStatus status);
    
    /**
     * Find execution status by executionId
     * @param executionId The execution ID
     * @return ExecutionStatus if found, null otherwise
     */
    ExecutionStatus findById(String executionId);
    
    /**
     * Delete execution status by executionId
     * @param executionId The execution ID
     */
    void deleteById(String executionId);
    
    /**
     * Check if execution status exists
     * @param executionId The execution ID
     * @return true if exists, false otherwise
     */
    boolean exists(String executionId);
}
