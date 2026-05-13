package com.epam.execution_engine_service.exception;

/**
 * ContainerAcquisitionException — Thrown when container pool cannot provide a container within timeout
 * 
 * Indicates a resource contention issue; should trigger proper error response to client.
 */
public class ContainerAcquisitionException extends ExecutionEngineException {

    public ContainerAcquisitionException(String message) {
        super(message);
    }

    public ContainerAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }

}
