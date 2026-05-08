package com.epam.execution_engine_service.persistence.entity;

/**
 * Enum representing the status of an execution.
 * PENDING: Execution request received, waiting to be processed
 * RUNNING: Execution is currently being processed
 * COMPLETED: Execution has completed
 * FAILED: Execution has failed
 */
public enum ExecutionStatusEnum {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    private final String status;

    ExecutionStatusEnum(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
