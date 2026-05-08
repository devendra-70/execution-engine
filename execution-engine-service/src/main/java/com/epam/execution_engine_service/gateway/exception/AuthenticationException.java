package com.epam.execution_engine_service.gateway.exception;

/**
 * Exception thrown when JWT authentication fails
 */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
