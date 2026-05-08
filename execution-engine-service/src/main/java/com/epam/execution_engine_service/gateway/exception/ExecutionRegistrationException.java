package com.epam.execution_engine_service.gateway.exception;

/**
 * Exception thrown when execution registration fails
 * (e.g., Redis write failure, Kafka publish failure)
 */
public class ExecutionRegistrationException extends RuntimeException {
    public ExecutionRegistrationException(String message) {
        super(message);
    }

    public ExecutionRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
