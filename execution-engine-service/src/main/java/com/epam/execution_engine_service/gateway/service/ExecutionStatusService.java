package com.epam.execution_engine_service.gateway.service;

import java.util.Optional;

/**
 * DIP: Abstraction for looking up execution status.
 * The REST controller depends on this interface, not on Redis directly.
 */
public interface ExecutionStatusService {

    /**
     * Return the current status string for the given execution ID,
     * or {@link Optional#empty()} if it is not found.
     */
    Optional<String> getStatus(String executionId);
}

