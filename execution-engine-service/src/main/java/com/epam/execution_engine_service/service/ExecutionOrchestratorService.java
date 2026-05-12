package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.*;
import com.epam.execution_engine_service.util.VerdictAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ExecutionOrchestratorService — Orchestration pipeline (SRS §6, §11, §14, §4)
 * 
 * Orchestrates the complete execution flow:
 * 1. Update Redis status to RUNNING (Step 4, SRS §8)
 * 2. Fetch test cases from cache (Step 8, SRS §4.2)
 * 3. Acquire container from pool (Step 9, SRS §4.3)
 * 4. Execute via socket-based communication (Step 10, SRS §6.2, §10)
 * 5. Aggregate verdict (Step 11, SRS §11)
 * 6. Return ExecutionResultEvent (Step 12)
 * 7. Persistence and Redis updates handled by PersistenceService
 * 8. WebSocket broadcasting handled by ExecutionResultBroadcaster
 * 
 * This service runs in Pool B (TaskExecutor thread pool).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionOrchestratorService {

    // Configuration constants (SRS §12, §4.1)
    private static final String REDIS_STATUS_RUNNING = "RUNNING";
    private static final String REDIS_STATUS_COMPLETED = "COMPLETED";
    private static final long CONTAINER_ACQUIRE_TIMEOUT_SECONDS = 5;
    private static final String EXECUTION_MODE_CLASS_NAME = "Solution";
    private static final String VERDICT_UNKNOWN = "UNKNOWN";
    private static final String VERDICT_RUNTIME_ERROR = "RUNTIME_ERROR";
    private static final double ERROR_SCORE = 0.0;

    private final TestCaseService testCaseService;
    private final ContainerPoolService containerPoolService;
    private final VerdictAggregator verdictAggregator;
    private final RedisExecutionStatusService redisStatusService;
    private final SandboxClient sandboxClient;
    private final ExecutionResultBroadcaster resultBroadcaster;

    @Value("${app.execution.timeout-ms:3000}")
    private long executionTimeoutMs;
    
    @Value("${app.sandbox.host:localhost}")
    private String sandboxHost;
    
    @Value("${app.sandbox.port:9999}")
    private int sandboxPort;

    /**
     * Execute a submission orchestration (SRS §11-14)
     * 
     * @param event ExecutionTaskEvent from Kafka
     * @return ExecutionResultEvent with final verdict and test case results
     */
    public ExecutionResultEvent execute(ExecutionTaskEvent event) {
        log.info("Starting execution orchestration for executionId: {}, problemId: {}, userId: {}", 
                event.getExecutionId(), event.getProblemId(), event.getUserId());

        long startTime = System.currentTimeMillis();
        ExecutionResultEvent.ExecutionResultEventBuilder resultBuilder = ExecutionResultEvent.builder()
                .executionId(event.getExecutionId())
                .userId(event.getUserId())
                .problemId(event.getProblemId())
                .completedAt(Instant.now());

        try {
            // Step 4: Update Redis status to RUNNING (SRS §8)
            redisStatusService.setStatus(event.getExecutionId(), REDIS_STATUS_RUNNING);
            log.debug("Status updated to RUNNING in Redis: executionId={}", event.getExecutionId());

            // Step 8: Fetch test cases from Caffeine cache (SRS §4.2, §8)
            List<TestCaseDto> testCases = testCaseService.getTestCases(event.getProblemId());
            
            if (testCases.isEmpty()) {
                log.warn("No test cases found for problemId: {}", event.getProblemId());
                return resultBuilder
                        .verdict(VERDICT_UNKNOWN)
                        .score(ERROR_SCORE)
                        .testCaseResults(new ArrayList<>())
                        .totalRuntimeMs(0L)
                        .totalMemoryBytes(0L)
                        .build();
            }

            log.debug("Retrieved {} test cases from cache for problemId: {}", testCases.size(), event.getProblemId());

            // Step 9: Acquire container from pool (SRS §4.3)
            ContainerPoolService.ContainerHandle container;
            try {
                container = containerPoolService.acquire(java.time.Duration.ofSeconds(CONTAINER_ACQUIRE_TIMEOUT_SECONDS));
                log.debug("Container acquired: {}", container.getId());
            } catch (Exception e) {
                log.error("Failed to acquire container", e);
                return resultBuilder
                        .verdict(VERDICT_RUNTIME_ERROR)
                        .score(ERROR_SCORE)
                        .testCaseResults(new ArrayList<>())
                        .totalRuntimeMs(0L)
                        .totalMemoryBytes(0L)
                        .build();
            }

            // Step 10: Execution loop via socket-based sandbox communication (SRS §10, §6.2)
            List<TestCaseResultDto> testCaseResults = executeViaSocket(
                    event.getExecutionId(),
                    event.getSourceCode(),
                    testCases,
                    EXECUTION_MODE_CLASS_NAME
            );

            // Release container back to pool
            containerPoolService.release(container);
            log.debug("Container released: {}", container.getId());

            // Step 11: Aggregate verdict (SRS §11)
            String verdict = verdictAggregator.aggregateVerdict(testCaseResults);
            long passCount = testCaseResults.stream()
                    .filter(r -> "PASS".equalsIgnoreCase(r.getStatus()))
                    .count();
            Double score = verdictAggregator.calculateScore(passCount, testCaseResults.size());

            long duration = System.currentTimeMillis() - startTime;
            long totalRuntime = testCaseResults.stream()
                    .mapToLong(TestCaseResultDto::getExecutionTimeMs)
                    .sum();
            long totalMemory = testCaseResults.stream()
                    .mapToLong(TestCaseResultDto::getMemoryBytes)
                    .max()
                    .orElse(0L);

            log.info("Execution orchestration completed: executionId={}, verdict={}, score={}, duration={}ms, passCount={}/{}", 
                    event.getExecutionId(), verdict, score, duration, passCount, testCaseResults.size());

            ExecutionResultEvent result = resultBuilder
                    .verdict(verdict)
                    .score(score)
                    .testCaseResults(testCaseResults)
                    .totalRuntimeMs(totalRuntime)
                    .totalMemoryBytes(totalMemory)
                    .build();

            // Step 14: Broadcast result to client (async, non-blocking)
            try {
                resultBroadcaster.broadcastResult(event.getUserId(), result);
            } catch (Exception e) {
                log.warn("Failed to broadcast result to client", e);
                // Don't fail orchestration if broadcast fails
            }

            return result;

        } catch (Exception e) {
            log.error("Execution orchestration failed: executionId={}", event.getExecutionId(), e);
            return resultBuilder
                    .verdict("RUNTIME_ERROR")
                    .score(0.0)
                    .testCaseResults(new ArrayList<>())
                    .totalRuntimeMs(0L)
                    .totalMemoryBytes(0L)
                    .build();
        } finally {
            // Update Redis status to COMPLETED
            redisStatusService.setStatus(event.getExecutionId(), "COMPLETED");
        }
    }

    /**
     * Execute test cases via socket communication with sandbox wrapper (SRS §10, §6.2)
     * 
     * @param executionId Unique execution identifier
     * @param sourceCode Java source code to execute
     * @param testCases Test cases to run
     * @param className Compiled class name (e.g., "Solution")
     * @return List of TestCaseResultDto with execution results
     */
    private List<TestCaseResultDto> executeViaSocket(
            UUID executionId,
            String sourceCode,
            List<TestCaseDto> testCases,
            String className) {

        // Convert TestCaseDto to SandboxClient.SandboxTestCase
        List<SandboxClient.SandboxTestCase> sandboxTestCases = testCases.stream()
                .map(tc -> SandboxClient.SandboxTestCase.builder()
                        .id(tc.getId().toString())
                        .input(tc.getInput())
                        .timeoutMs(tc.getTimeoutMs())
                        .build())
                .toList();

        // Execute via socket
        List<TestCaseResultDto> results = sandboxClient.executeViaSocket(
                sandboxHost,
                sandboxPort,
                executionId,
                className,
                sourceCode,
                sandboxTestCases
        );

        // Map results to expected format and compare with expected output
        return results.stream()
                .map(result -> {
                    // Find corresponding test case for expected output
                    TestCaseDto testCase = testCases.stream()
                            .filter(tc -> tc.getId().toString().equals(result.getTestCaseId().toString()))
                            .findFirst()
                            .orElse(null);
                    
                    if (testCase != null) {
                        result.setExpectedOutput(testCase.getExpectedOutput());
                        
                        // Determine pass/fail based on actual vs expected
                        if ("OK".equalsIgnoreCase(result.getStatus()) && 
                            testCase.getExpectedOutput().equals(result.getActualOutput())) {
                            result.setStatus("PASS");
                        } else if ("OK".equalsIgnoreCase(result.getStatus())) {
                            result.setStatus("WRONG_ANSWER");
                        }
                    }
                    
                    return result;
                })
                .toList();
    }

}
