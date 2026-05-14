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
import java.util.concurrent.*;

/**
 * ExecutionOrchestratorService — Pool B orchestration pipeline (SRS §4.1, §4.3, §6, §10, §12).
 *
 * <p>Implements the single-container submission lifecycle required by EPMICMPCOD-533:
 * <ol>
 *   <li>Update Redis status to RUNNING.</li>
 *   <li>Fetch test cases from Caffeine cache (SRS §4.2).</li>
 *   <li>Acquire <em>one</em> container from the pool for the entire submission (SRS §4.3).</li>
 *   <li>Execute: send source code once; iterate test cases via socket; new ClassLoader per
 *       test case inside the wrapper (SRS §6.2).</li>
 *   <li>Aggregate verdict (SRS §11).</li>
 *   <li>On TLE: SIGKILL container via {@link ContainerPoolService#discardAndReplace(ContainerPoolService.ContainerHandle)};
 *       never return to pool (SRS §10, EPMICMPCOD-532).</li>
 *   <li>On COMPILE_ERROR or normal completion: {@link ContainerPoolService#release(ContainerPoolService.ContainerHandle)}
 *       (container healthy — EPMICMPCOD-533 AC 4).</li>
 *   <li>Broadcast result via WebSocket.</li>
 * </ol>
 *
 * <p>All timeout values are strictly sourced from {@code app.execution.timeout-ms} — never
 * hardcoded (SRS §12).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionOrchestratorService {

    private static final String REDIS_STATUS_RUNNING   = "RUNNING";
    private static final String REDIS_STATUS_COMPLETED = "COMPLETED";
    private static final long   CONTAINER_ACQUIRE_TIMEOUT_SECONDS = 5;
    private static final String EXECUTION_MODE_CLASS_NAME = "Solution";
    private static final String VERDICT_UNKNOWN       = "UNKNOWN";
    private static final String VERDICT_RUNTIME_ERROR = "RUNTIME_ERROR";
    private static final String VERDICT_TLE           = "TIME_LIMIT_EXCEEDED";
    private static final String VERDICT_COMPILE_ERROR = "COMPILE_ERROR";
    private static final double ERROR_SCORE = 0.0;

    private final TestCaseService testCaseService;
    private final ContainerPoolService containerPoolService;
    private final VerdictAggregator verdictAggregator;
    private final RedisExecutionStatusService redisStatusService;
    private final SandboxClient sandboxClient;
    private final ExecutionResultBroadcaster resultBroadcaster;

    /**
     * Hard timeout per submission sourced from {@code app.execution.timeout-ms} (SRS §12).
     * Default: 3000 ms.
     */
    @Value("${app.execution.timeout-ms:3000}")
    private long executionTimeoutMs;

    /**
     * Executor dedicated to the blocking sandbox socket call so that the TLE
     * {@link Future#get(long, TimeUnit)} enforces the hard timeout without blocking
     * the Pool B thread indefinitely.
     */
    private final ExecutorService sandboxExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "sandbox-exec");
                t.setDaemon(true);
                return t;
            });

    /**
     * Execute a submission — entry point called from Pool B (SRS §4.1).
     *
     * <p>Uses <em>exactly one</em> container from acquisition to return/discard
     * (EPMICMPCOD-533 AC 1, AC 6).
     *
     * @param event {@link ExecutionTaskEvent} forwarded from the Kafka listener
     * @return {@link ExecutionResultEvent} with aggregated verdict and per-test-case results
     */
    public ExecutionResultEvent execute(ExecutionTaskEvent event) {
        log.info("Starting execution orchestration: executionId={}, problemId={}, userId={}",
                event.getExecutionId(), event.getProblemId(), event.getUserId());

        long startTime = System.currentTimeMillis();
        ExecutionResultEvent.ExecutionResultEventBuilder resultBuilder = ExecutionResultEvent.builder()
                .executionId(event.getExecutionId())
                .userId(event.getUserId())
                .problemId(event.getProblemId())
                .completedAt(Instant.now());

        try {
            // Step 1: Redis → RUNNING
            redisStatusService.setStatus(event.getExecutionId(), REDIS_STATUS_RUNNING);

            // Step 2: Fetch test cases from Caffeine cache (SRS §4.2)
            List<TestCaseDto> testCases = testCaseService.getTestCases(event.getProblemId());
            if (testCases.isEmpty()) {
                log.warn("No test cases found for problemId={}", event.getProblemId());
                return resultBuilder
                        .verdict(VERDICT_UNKNOWN)
                        .score(ERROR_SCORE)
                        .testCaseResults(new ArrayList<>())
                        .totalRuntimeMs(0L)
                        .totalMemoryBytes(0L)
                        .build();
            }

            // Step 3: Acquire one container for the whole submission (SRS §4.3, EPMICMPCOD-533 AC 1)
            ContainerPoolService.ContainerHandle container;
            try {
                container = containerPoolService.acquire(
                        java.time.Duration.ofSeconds(CONTAINER_ACQUIRE_TIMEOUT_SECONDS));
                log.debug("Container acquired: id={}, port={}", container.getId(), container.getPort());
            } catch (Exception e) {
                log.error("Failed to acquire container for executionId={}", event.getExecutionId(), e);
                return resultBuilder
                        .verdict(VERDICT_RUNTIME_ERROR)
                        .score(ERROR_SCORE)
                        .testCaseResults(new ArrayList<>())
                        .totalRuntimeMs(0L)
                        .totalMemoryBytes(0L)
                        .build();
            }

            // Step 4: Execute via socket — wrapped in a Future to enforce TLE timeout (SRS §10)
            final ContainerPoolService.ContainerHandle finalContainer = container;
            final UUID executionId = event.getExecutionId();

            Future<List<TestCaseResultDto>> future = sandboxExecutor.submit(() ->
                    executeViaSocket(
                            executionId,
                            event.getSourceCode(),
                            testCases,
                            EXECUTION_MODE_CLASS_NAME,
                            finalContainer.getHost(),
                            finalContainer.getPort()
                    )
            );

            List<TestCaseResultDto> testCaseResults;
            try {
                testCaseResults = future.get(executionTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // TLE — SIGKILL the container; it is never returned to the pool (EPMICMPCOD-532)
                future.cancel(true);
                long elapsed = System.currentTimeMillis() - startTime;
                log.warn("TLE: executionId={}, containerId={}, elapsedMs={}",
                        executionId, container.getContainerId(), elapsed);
                containerPoolService.discardAndReplace(container);

                return resultBuilder
                        .verdict(VERDICT_TLE)
                        .score(ERROR_SCORE)
                        .testCaseResults(new ArrayList<>())
                        .totalRuntimeMs(elapsed)
                        .totalMemoryBytes(0L)
                        .build();
            } catch (ExecutionException e) {
                log.error("Sandbox execution exception for executionId={}", executionId, e.getCause());
                containerPoolService.release(container);
                return resultBuilder
                        .verdict(VERDICT_RUNTIME_ERROR)
                        .score(ERROR_SCORE)
                        .testCaseResults(new ArrayList<>())
                        .totalRuntimeMs(System.currentTimeMillis() - startTime)
                        .totalMemoryBytes(0L)
                        .build();
            }

            // COMPILE_ERROR — container is still healthy; return it to the pool (EPMICMPCOD-533 AC 4)
            boolean isCompileError = !testCaseResults.isEmpty() &&
                    VERDICT_COMPILE_ERROR.equalsIgnoreCase(testCaseResults.get(0).getStatus());

            // Step 5: Release container (healthy after success or compile error) (EPMICMPCOD-533 AC 6)
            containerPoolService.release(container);
            log.debug("Container released: id={}", container.getId());

            if (isCompileError) {
                log.info("Compile error for executionId={}", executionId);
                return resultBuilder
                        .verdict(VERDICT_COMPILE_ERROR)
                        .score(ERROR_SCORE)
                        .testCaseResults(testCaseResults)
                        .totalRuntimeMs(System.currentTimeMillis() - startTime)
                        .totalMemoryBytes(0L)
                        .build();
            }

            // Step 6: Aggregate verdict (SRS §11)
            String verdict = verdictAggregator.aggregateVerdict(testCaseResults);
            long passCount = testCaseResults.stream()
                    .filter(r -> "PASS".equalsIgnoreCase(r.getStatus()))
                    .count();
            Double score = verdictAggregator.calculateScore(passCount, testCaseResults.size());

            long totalRuntime = testCaseResults.stream()
                    .mapToLong(TestCaseResultDto::getExecutionTimeMs)
                    .sum();
            long totalMemory = testCaseResults.stream()
                    .mapToLong(TestCaseResultDto::getMemoryBytes)
                    .max()
                    .orElse(0L);
            long duration = System.currentTimeMillis() - startTime;

            log.info("Orchestration complete: executionId={}, verdict={}, score={}, durationMs={}, pass={}/{}",
                    executionId, verdict, score, duration, passCount, testCaseResults.size());

            ExecutionResultEvent result = resultBuilder
                    .verdict(verdict)
                    .score(score)
                    .testCaseResults(testCaseResults)
                    .totalRuntimeMs(totalRuntime)
                    .totalMemoryBytes(totalMemory)
                    .build();

            // Step 7: Broadcast result (async, best-effort)
            try {
                resultBroadcaster.broadcastResult(event.getUserId(), result);
            } catch (Exception e) {
                log.warn("Failed to broadcast result for executionId={}: {}", executionId, e.getMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("Execution orchestration failed: executionId={}", event.getExecutionId(), e);
            return resultBuilder
                    .verdict(VERDICT_RUNTIME_ERROR)
                    .score(ERROR_SCORE)
                    .testCaseResults(new ArrayList<>())
                    .totalRuntimeMs(0L)
                    .totalMemoryBytes(0L)
                    .build();
        } finally {
            redisStatusService.setStatus(event.getExecutionId(), REDIS_STATUS_COMPLETED);
        }
    }

    /**
     * Delegate to {@link SandboxClient#executeViaSocket} using the per-container host and port
     * obtained from the acquired {@link ContainerPoolService.ContainerHandle} (EPMICMPCOD-533).
     *
     * <p>Source code is sent once; the wrapper compiles once; test cases are fed iteratively;
     * each test case uses a new {@code URLClassLoader} inside the wrapper (SRS §6.2).
     *
     * @param executionId unique execution identifier
     * @param sourceCode  Java source to compile and run
     * @param testCases   ordered list of test cases for the submission
     * @param className   solution class name (always {@code "Solution"})
     * @param host        sandbox container host
     * @param port        sandbox container port
     * @return list of per-test-case results
     */
    private List<TestCaseResultDto> executeViaSocket(
            UUID executionId,
            String sourceCode,
            List<TestCaseDto> testCases,
            String className,
            String host,
            int port) {

        List<SandboxClient.SandboxTestCase> sandboxTestCases = testCases.stream()
                .map(tc -> SandboxClient.SandboxTestCase.builder()
                        .id(tc.getId().toString())
                        .input(tc.getInput())
                        .timeoutMs(tc.getTimeoutMs())
                        .build())
                .toList();

        List<TestCaseResultDto> results = sandboxClient.executeViaSocket(
                host, port, executionId, className, sourceCode, sandboxTestCases);

        // Map actual vs expected and derive PASS / WRONG_ANSWER
        return results.stream()
                .map(result -> {
                    TestCaseDto testCase = testCases.stream()
                            .filter(tc -> tc.getId().toString().equals(result.getTestCaseId() != null
                                    ? result.getTestCaseId().toString() : ""))
                            .findFirst()
                            .orElse(null);

                    if (testCase != null) {
                        result.setExpectedOutput(testCase.getExpectedOutput());
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
