package com.epam.execution_engine_service.orchestrator;

import com.epam.execution_engine_service.domain.*;
import com.epam.execution_engine_service.orchestrator.cache.TestCaseCacheService;
import com.epam.execution_engine_service.orchestrator.docker.ContainerPool;
import com.epam.execution_engine_service.orchestrator.publisher.ExecutionResultPublisher;
import com.epam.execution_engine_service.orchestrator.strategy.CodeExecutionStrategy;
import com.epam.execution_engine_service.orchestrator.verdict.VerdictAggregator;
import com.epam.execution_engine_service.persistence.service.PersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SRP: Thin orchestrator — coordinates the execution pipeline.
 * Individual concerns (strategy selection, verdict aggregation, result publishing)
 * are delegated to dedicated collaborators, satisfying SRP and DIP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionOrchestrationService {

    private final TestCaseCacheService testCaseCacheService;
    private final ContainerPool containerPool;
    private final PersistenceService persistenceService;
    private final ExecutionResultPublisher resultPublisher;
    private final VerdictAggregator verdictAggregator;
    /** All {@link CodeExecutionStrategy} beans injected in @Order-defined priority. */
    private final List<CodeExecutionStrategy> executionStrategies;

    @Value("${app.execution.timeout-ms:3000}")
    private long timeoutMs;

    /**
     * Full orchestration flow for a single submission.
     * Runs on the orchestration thread pool.
     * Throws on DB failure so the Kafka offset is NOT committed.
     */
    public void orchestrate(ExecutionTaskEvent taskEvent) throws Exception {
        log.info("Orchestrating execution {} for problem {}", taskEvent.getExecutionId(), taskEvent.getProblemId());

        // 1. Fetch test cases based on mode
        boolean isRunMode = "run".equalsIgnoreCase(taskEvent.getMode());
        List<TestCase> testCases = isRunMode
                ? testCaseCacheService.getVisibleTestCases(taskEvent.getProblemId())
                : testCaseCacheService.getTestCases(taskEvent.getProblemId());

        if (testCases.isEmpty()) {
            log.warn("No test cases found for problemId {}", taskEvent.getProblemId());
        }

        // 2. Select execution strategy (OCP: new strategies are picked up automatically)
        boolean dockerAvailable = containerPool.isDockerAvailable();
        CodeExecutionStrategy strategy = executionStrategies.stream()
                .filter(s -> s.supports(dockerAvailable))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No CodeExecutionStrategy available"));

        List<TestCaseResultEvent> testCaseResults = strategy.execute(
                taskEvent.getSourceCode(), testCases, timeoutMs);

        // 3. Aggregate final verdict and score
        Verdict finalVerdict = verdictAggregator.aggregate(testCaseResults);
        int score = verdictAggregator.calculateScore(testCaseResults);
        long totalRuntime = testCaseResults.stream().mapToLong(TestCaseResultEvent::getRuntimeMs).sum();
        long maxMemory   = testCaseResults.stream().mapToLong(TestCaseResultEvent::getMemoryBytes).max().orElse(0);

        ExecutionResultEvent resultEvent = ExecutionResultEvent.builder()
                .executionId(taskEvent.getExecutionId())
                .userId(taskEvent.getUserId())
                .problemId(taskEvent.getProblemId())
                .problemName("Problem " + taskEvent.getProblemId())
                .verdict(finalVerdict)
                .score(score)
                .totalRuntimeMs(totalRuntime)
                .memoryBytes(maxMemory)
                .testCaseResults(testCaseResults)
                .build();

        // 4. Persist to PostgreSQL (throws on failure → Kafka offset not committed)
        persistenceService.saveSubmission(taskEvent, resultEvent);

        // 5. Publish status + result via publisher abstraction
        resultPublisher.publishResult(resultEvent);

        log.info("Orchestration complete for executionId={}, verdict={}", taskEvent.getExecutionId(), finalVerdict);
    }
}
