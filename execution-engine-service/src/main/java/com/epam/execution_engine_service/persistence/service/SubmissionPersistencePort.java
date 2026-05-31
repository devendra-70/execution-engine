package com.epam.execution_engine_service.persistence.service;

import com.epam.execution_engine_service.domain.ExecutionResultEvent;
import com.epam.execution_engine_service.domain.ExecutionTaskEvent;

/**
 * DIP: Abstraction for persisting execution results.
 * The orchestrator depends on this port, not the JPA/Hibernate implementation.
 */
public interface SubmissionPersistencePort {

    /**
     * Persist a completed execution with all its test-case results.
     * Must be transactional; throws on failure so the Kafka offset is not committed.
     */
    void saveSubmission(ExecutionTaskEvent taskEvent, ExecutionResultEvent resultEvent);
}

