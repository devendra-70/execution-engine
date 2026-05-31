package com.epam.execution_engine_service.orchestrator.strategy;

import com.epam.execution_engine_service.domain.TestCase;
import com.epam.execution_engine_service.domain.TestCaseResultEvent;
import com.epam.execution_engine_service.domain.Verdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SRP/OCP: Handles execution when Docker is unavailable (dev/CI stub mode).
 * Marked {@code @Order(1)} so it is evaluated before the Docker strategy.
 */
@Slf4j
@Component
@Order(1)
public class StubExecutionStrategy implements CodeExecutionStrategy {

    @Override
    public boolean supports(boolean dockerAvailable) {
        return !dockerAvailable;
    }

    @Override
    public List<TestCaseResultEvent> execute(String sourceCode, List<TestCase> testCases, long timeoutMs) {
        log.warn("[StubExecution] Docker unavailable — returning stub ACCEPTED results");
        if (testCases.isEmpty()) {
            return List.of(TestCaseResultEvent.builder()
                    .testCaseId(0L)
                    .verdict(Verdict.ACCEPTED)
                    .actualOutput("(stub)")
                    .expectedOutput("(stub)")
                    .runtimeMs(1)
                    .memoryBytes(1024)
                    .build());
        }
        return testCases.stream()
                .map(tc -> TestCaseResultEvent.builder()
                        .testCaseId(tc.getId())
                        .verdict(Verdict.ACCEPTED)
                        .actualOutput("(stub)")
                        .expectedOutput(tc.getExpectedOutput())
                        .runtimeMs(1)
                        .memoryBytes(1024)
                        .build())
                .toList();
    }
}

