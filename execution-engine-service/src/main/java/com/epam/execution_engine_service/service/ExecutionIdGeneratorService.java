package com.epam.execution_engine_service.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * ExecutionIdGeneratorService
 * Generates unique execution IDs using UUID
 */
@Service
public class ExecutionIdGeneratorService {
    
    /**
     * Generates a new unique execution ID
     * @return UUID-based execution ID
     */
    public String generateExecutionId() {
        return UUID.randomUUID().toString();
    }
}
