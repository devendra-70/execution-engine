package com.epam.execution_engine_service.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ExecutionIdGenerator — UUID generation utility
 * 
 * Generates unique execution identifiers for each submission.
 * Uses standard Java UUID.randomUUID() for simplicity.
 */
@Component
@Slf4j
public class ExecutionIdGenerator {

    /**
     * Generate a new unique execution ID
     * @return UUID
     */
    public UUID generateExecutionId() {
        UUID executionId = UUID.randomUUID();
        log.debug("Generated execution ID: {}", executionId);
        return executionId;
    }

}
