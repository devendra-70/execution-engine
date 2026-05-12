package com.epam.execution_engine_service.gateway;

import com.epam.execution_engine_service.persistence.entity.ExecutionStatus;
import com.epam.execution_engine_service.persistence.repository.ExecutionStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * ExecutionStatusService (SRS §3.2)
 * Retrieves execution status from Redis KV store
 * Used for REST fallback when WebSocket connection is lost or unavailable
 */
@Service
@RequiredArgsConstructor
public class ExecutionStatusService {
    
    private final ExecutionStatusRepository executionStatusRepository;
    
    /**
     * Get execution status from Redis
     * @param executionId UUID of the execution
     * @return ExecutionStatusResponse with current status, or null if not found
     */
    public ExecutionStatusResponse getExecutionStatus(UUID executionId) {
        try {
            ExecutionStatus status = executionStatusRepository.findById(executionId.toString());
            
            if (status == null) {
                return null;
            }
            
            return ExecutionStatusResponse.builder()
                    .executionId(executionId.toString())
                    .status(status.getStatus().name())
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
