package com.epam.execution_engine_service.gateway.exception;

/**
 * Exception thrown when rate limit is exceeded for a user or IP
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
