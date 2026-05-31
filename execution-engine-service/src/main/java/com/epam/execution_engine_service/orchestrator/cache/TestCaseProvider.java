package com.epam.execution_engine_service.orchestrator.cache;

import com.epam.execution_engine_service.domain.TestCase;

import java.util.List;

/**
 * DIP: Abstraction for test-case retrieval.
 * The orchestrator depends on this interface, not the cache/DB implementation.
 */
public interface TestCaseProvider {

    /** All test cases for a problem (submit mode). */
    List<TestCase> getTestCases(Long problemId);

    /** Only visible (non-hidden) test cases (run mode). */
    List<TestCase> getVisibleTestCases(Long problemId);
}

