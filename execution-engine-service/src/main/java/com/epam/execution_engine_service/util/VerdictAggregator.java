package com.epam.execution_engine_service.util;

import com.epam.execution_engine_service.dto.TestCaseResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * VerdictAggregator — Aggregation logic for final verdict (SRS §6, §11)
 * 
 * Determines final verdict based on individual test case results:
 * - COMPILE_ERROR (any compile error) — short-circuit
 * - RUNTIME_ERROR (any runtime error, no compile error)
 * - TIME_LIMIT_EXCEEDED (any timeout, no compile/runtime error)
 * - PARTIAL_SUCCESS (some pass, some fail)
 * - PASSED (all pass)
 */
@Component
@Slf4j
public class VerdictAggregator {

    /**
     * Aggregate verdict from test case results
     * @param results List of test case results
     * @return Aggregated verdict string
     */
    public String aggregateVerdict(List<TestCaseResultDto> results) {
        if (results == null || results.isEmpty()) {
            log.warn("No test case results provided for verdict aggregation");
            return "UNKNOWN";
        }

        // Check for compile errors (short-circuit)
        if (results.stream().anyMatch(r -> "COMPILE_ERROR".equals(r.getStatus()))) {
            log.debug("Aggregated verdict: COMPILE_ERROR");
            return "COMPILE_ERROR";
        }

        // Check for runtime errors
        if (results.stream().anyMatch(r -> "RUNTIME_ERROR".equals(r.getStatus()))) {
            log.debug("Aggregated verdict: RUNTIME_ERROR");
            return "RUNTIME_ERROR";
        }

        // Check for timeouts
        if (results.stream().anyMatch(r -> "TIME_LIMIT_EXCEEDED".equals(r.getStatus()))) {
            log.debug("Aggregated verdict: TIME_LIMIT_EXCEEDED");
            return "TIME_LIMIT_EXCEEDED";
        }

        // Count pass/fail
        long passCount = results.stream()
                .filter(r -> "PASS".equals(r.getStatus()))
                .count();
        long totalCount = results.size();

        if (passCount == totalCount) {
            log.debug("Aggregated verdict: PASSED");
            return "PASSED";
        } else if (passCount > 0) {
            log.debug("Aggregated verdict: PARTIAL_SUCCESS ({}/{})", passCount, totalCount);
            return "PARTIAL_SUCCESS";
        } else {
            log.debug("Aggregated verdict: PARTIAL_SUCCESS (no passes)");
            return "PARTIAL_SUCCESS";
        }
    }

    /**
     * Calculate score as percentage
     * @param passCount Number of passed test cases
     * @param totalCount Total number of test cases
     * @return Score as percentage (0.0 - 100.0)
     */
    public Double calculateScore(long passCount, long totalCount) {
        if (totalCount == 0) {
            return 0.0;
        }
        Double score = (passCount * 100.0) / totalCount;
        log.debug("Calculated score: {} ({}/{} passed)", score, passCount, totalCount);
        return score;
    }

}
