package com.epam.execution_engine_service.orchestrator.verdict;

import com.epam.execution_engine_service.domain.TestCaseResultEvent;
import com.epam.execution_engine_service.domain.Verdict;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OCP-compliant verdict aggregator.
 * Priority order is data-driven: adding a new high-priority verdict only requires
 * inserting it into PRIORITY_ORDER — no conditional logic changes needed.
 */
@Component
public class PriorityVerdictAggregator implements VerdictAggregator {

    /**
     * Verdicts in descending priority: the first one found in results wins.
     */
    private static final List<Verdict> PRIORITY_ORDER = List.of(
            Verdict.COMPILE_ERROR,
            Verdict.TIME_LIMIT_EXCEEDED,
            Verdict.RUNTIME_ERROR,
            Verdict.WRONG_ANSWER
    );

    @Override
    public Verdict aggregate(List<TestCaseResultEvent> results) {
        if (results == null || results.isEmpty()) {
            return Verdict.WRONG_ANSWER;
        }
        for (Verdict priority : PRIORITY_ORDER) {
            if (results.stream().anyMatch(r -> r.getVerdict() == priority)) {
                return priority;
            }
        }
        return Verdict.ACCEPTED;
    }

    @Override
    public int calculateScore(List<TestCaseResultEvent> results) {
        if (results == null || results.isEmpty()) return 0;
        long passed = results.stream().filter(r -> r.getVerdict() == Verdict.ACCEPTED).count();
        return (int) ((passed * 100) / results.size());
    }
}

