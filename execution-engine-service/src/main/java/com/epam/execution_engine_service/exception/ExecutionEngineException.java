package com.epam.execution_engine_service.exception;

/**
 * ExecutionEngineException — Base exception for the Execution Engine service
 * 
 * All service-layer exceptions inherit from this class.
 */
public class ExecutionEngineException extends RuntimeException {

    public ExecutionEngineException(String message) {
        super(message);
    }

    public ExecutionEngineException(String message, Throwable cause) {
        super(message, cause);
    }

}
