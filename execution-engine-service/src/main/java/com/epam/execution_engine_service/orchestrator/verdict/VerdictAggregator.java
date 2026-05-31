package com.epam.execution_engine_service.orchestrator.verdict;

import com.epam.execution_engine_service.domain.TestCaseResultEvent;
import com.epam.execution_engine_service.domain.Verdict;

import java.util.List;

/**
 * OCP/DIP: Abstraction for verdict aggregation.
 * New verdict rules can be added by implementing this interface or extending the priority list,
 * without modifying the orchestrator.
 */
public interface VerdictAggregator {

    /**
     * Compute the overall verdict from individual test-case results.
     */
    Verdict aggregate(List<TestCaseResultEvent> results);

    /**
     * Compute a 0-100 score based on how many test cases passed.
     */
    int calculateScore(List<TestCaseResultEvent> results);
}

