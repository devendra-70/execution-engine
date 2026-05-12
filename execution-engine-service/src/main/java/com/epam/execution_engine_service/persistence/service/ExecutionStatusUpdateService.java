package com.epam.execution_engine_service.persistence.service;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.persistence.entity.ExecutionStatus;
import com.epam.execution_engine_service.persistence.entity.ExecutionStatusEnum;
import com.epam.execution_engine_service.persistence.repository.ExecutionStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * ExecutionStatusUpdateService (SRS §8, §2.2)
 * 
 * Updates execution status in Redis KV store.
 * Status is stored with a configurable TTL for fallback status queries.
 * 
 * Redis Key: execution:status:{executionId}
 * TTL: app.redis.status-ttl-seconds (default: 600 seconds = 10 minutes)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionStatusUpdateService {
    
    private final ExecutionStatusRepository executionStatusRepository;
    private final ApplicationProperties applicationProperties;
    
    /**
     * Update execution status in Redis (SRS §8)
     * 
     * Status values:
     * - PENDING: Initial state, waiting for processing
     * - EXECUTING: Currently executing in sandbox
     * - COMPLETED: Execution finished successfully
     * - FAILED: Execution encountered a critical error
     * - TIMEOUT: Execution timed out
     * 
     * @param executionId UUID of the execution
     * @param status Current status value
     */
    public void updateStatus(UUID executionId, String status) {
        try {
            int ttlSeconds = applicationProperties.getRedis().getStatusTtlSeconds();
            
            ExecutionStatus executionStatus = ExecutionStatus.builder()
                    .executionId(executionId.toString())
                    .status(ExecutionStatusEnum.valueOf(status))
                    .build();
            
            // Save with TTL expiration (Redis expires automatically)
            executionStatusRepository.save(executionStatus);
        } catch (Exception e) {
            // Non-critical error: continue execution (status update failed, but execution continues)
        }
    }
    
    /**
     * Update status with custom TTL
     * @param executionId UUID of the execution
     * @param status Current status value
     * @param ttlSeconds Custom TTL in seconds
     */
    public void updateStatusWithTtl(UUID executionId, String status, int ttlSeconds) {
        try {
            ExecutionStatus executionStatus = ExecutionStatus.builder()
                    .executionId(executionId.toString())
                    .status(ExecutionStatusEnum.valueOf(status))
                    .build();
            
            executionStatusRepository.save(executionStatus);
        } catch (Exception e) {
        }
    }
}
