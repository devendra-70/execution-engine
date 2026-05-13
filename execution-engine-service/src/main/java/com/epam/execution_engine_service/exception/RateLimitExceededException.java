package com.epam.execution_engine_service.exception;

/**
 * RateLimitExceededException — Thrown when rate limit is exceeded (SRS §3.3)
 * 
 * Should result in HTTP 429 Too Many Requests response.
 */
public class RateLimitExceededException extends ExecutionEngineException {

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }

}
