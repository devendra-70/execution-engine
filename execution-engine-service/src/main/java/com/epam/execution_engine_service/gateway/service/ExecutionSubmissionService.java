package com.epam.execution_engine_service.gateway.service;

import com.epam.execution_engine_service.domain.ExecutionRequest;

import java.util.UUID;

/**
 * DIP/SRP: Abstraction for the submission use-case.
 * The REST controller depends on this interface, not on Kafka or Redis directly.
 */
public interface ExecutionSubmissionService {

    /**
     * Accept a submission, write PENDING status, publish to Kafka, and return the execution ID.
     *
     * @param request the validated submission payload
     * @param userId  the authenticated user's ID
     * @return a new {@link UUID} identifying this execution
     */
    UUID submit(ExecutionRequest request, Long userId);
}

