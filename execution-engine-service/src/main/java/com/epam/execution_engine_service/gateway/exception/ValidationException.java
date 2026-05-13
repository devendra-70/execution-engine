package com.epam.execution_engine_service.gateway.exception;

/**
 * Exception thrown when validation of ExecutionRequest fails
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
