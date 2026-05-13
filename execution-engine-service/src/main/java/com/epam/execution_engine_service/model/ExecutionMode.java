package com.epam.execution_engine_service.model;

/**
 * Enumeration representing the execution mode for a code submission.
 *
 * <p>RUN mode is for preview/test evaluation without scoring or persistence.
 * SUBMIT mode is for final submission with scoring and result persistence.
 *
 * @author Execution Engine Team
 */
public enum ExecutionMode {
    /**
     * Preview mode: Execute test cases but do not score or persist results.
     */
    RUN,

    /**
     * Submit mode: Execute test cases, compute score, and persist results to database.
     */
    SUBMIT
}
