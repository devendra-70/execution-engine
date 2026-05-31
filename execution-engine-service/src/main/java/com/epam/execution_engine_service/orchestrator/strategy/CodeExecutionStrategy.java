package com.epam.execution_engine_service.orchestrator.strategy;

import com.epam.execution_engine_service.domain.TestCase;
import com.epam.execution_engine_service.domain.TestCaseResultEvent;

import java.util.List;

/**
 * OCP/DIP: Strategy abstraction for code execution.
 * New execution backends (e.g., Kubernetes pods, WASM) can be added by implementing
 * this interface without changing the orchestrator.
 */
public interface CodeExecutionStrategy {

    /**
     * Returns {@code true} when this strategy should be used given the current environment.
     *
     * @param dockerAvailable whether a live Docker daemon was detected at startup
     */
    boolean supports(boolean dockerAvailable);

    /**
     * Execute {@code sourceCode} against each test case and return per-case results.
     */
    List<TestCaseResultEvent> execute(String sourceCode, List<TestCase> testCases, long timeoutMs) throws Exception;
}

