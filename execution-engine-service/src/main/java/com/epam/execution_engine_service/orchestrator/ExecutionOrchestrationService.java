package com.epam.execution_engine_service.orchestrator;

import com.epam.execution_engine_service.domain.*;
import com.epam.execution_engine_service.orchestrator.cache.TestCaseCacheService;
import com.epam.execution_engine_service.orchestrator.docker.DockerContainerPool;
import com.epam.execution_engine_service.orchestrator.docker.SandboxContainer;
import com.epam.execution_engine_service.persistence.service.PersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionOrchestrationService {

    private static final String STUB_OUTPUT = "(stub)";

    private final TestCaseCacheService testCaseCacheService;
    private final DockerContainerPool containerPool;
    private final PersistenceService persistenceService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.execution.timeout-ms:3000}")
    private long timeoutMs;

    @Value("${app.redis.status-ttl-seconds:600}")
    private long statusTtlSeconds;

    /**
     * Full orchestration flow for a single submission.
     * This runs on Pool B (orchestration thread pool).
     * Throws exception if DB write fails, so Kafka offset is NOT committed.
     */
    public void orchestrate(ExecutionTaskEvent taskEvent) throws Exception {
        log.info("Orchestrating execution {} for problem {}", taskEvent.getExecutionId(), taskEvent.getProblemId());

        // 1. Fetch test cases — run mode uses only visible (non-hidden) test cases;
        //    submit mode uses all test cases (including hidden).
        boolean isRunMode = "run".equalsIgnoreCase(taskEvent.getMode());
        List<TestCase> testCases = isRunMode
                ? testCaseCacheService.getVisibleTestCases(taskEvent.getProblemId())
                : testCaseCacheService.getTestCases(taskEvent.getProblemId());

        if (testCases.isEmpty()) {
            log.warn("No test cases found for problemId {}", taskEvent.getProblemId());
        }

        List<TestCaseResultEvent> testCaseResults;
        String problemName = "Problem " + taskEvent.getProblemId();

        if (!containerPool.isDockerAvailable()) {
            // Stub mode: Docker not available (dev/CI environment)
            log.warn("Running in STUB mode - Docker unavailable");
            testCaseResults = runStubExecution(testCases);
        } else if (testCases.isEmpty()) {
            log.warn("No test cases for problemId {} — returning stub ACCEPTED", taskEvent.getProblemId());
            testCaseResults = runStubExecution(testCases);
        } else {
            // 2. Acquire sandbox container from pool
            log.info("[Orchestrator] Acquiring sandbox container (pool size={})", containerPool.getPoolSize());
            SandboxContainer container = containerPool.acquire(timeoutMs + 5000);
            log.info("[Orchestrator] Acquired container {} for executionId={}",
                    container.getContainerId(), taskEvent.getExecutionId());
            try {
                testCaseResults = container.execute(taskEvent.getSourceCode(), testCases, timeoutMs);
            } finally {
                // ALWAYS release — even on TLE, OOM, or runtime exception
                containerPool.release(container);
                log.info("[Orchestrator] Released container {} (pool size={})",
                        container.getContainerId(), containerPool.getPoolSize());
            }
        }

        // 3. Aggregate final verdict
        Verdict finalVerdict = aggregateVerdict(testCaseResults);
        int score = calculateScore(testCaseResults);
        long totalRuntime = testCaseResults.stream().mapToLong(TestCaseResultEvent::getRuntimeMs).sum();
        long maxMemory = testCaseResults.stream().mapToLong(TestCaseResultEvent::getMemoryBytes).max().orElse(0);

        ExecutionResultEvent resultEvent = ExecutionResultEvent.builder()
                .executionId(taskEvent.getExecutionId())
                .userId(taskEvent.getUserId())
                .problemId(taskEvent.getProblemId())
                .problemName(problemName)
                .verdict(finalVerdict)
                .score(score)
                .totalRuntimeMs(totalRuntime)
                .memoryBytes(maxMemory)
                .testCaseResults(testCaseResults)
                .build();

        // 4. Persist to PostgreSQL (throws on failure → Kafka offset not committed)
        persistenceService.saveSubmission(taskEvent, resultEvent);

        // 5. Update Redis KV status
        String redisKey = "execution:status:" + taskEvent.getExecutionId();
        redisTemplate.opsForValue().set(redisKey, ExecutionStatus.COMPLETED.name(),
                Duration.ofSeconds(statusTtlSeconds));

        // 6. Publish to Redis Pub/Sub for WebSocket delivery
        String resultJson = objectMapper.writeValueAsString(resultEvent);
        redisTemplate.convertAndSend("execution-completed", resultJson);

        log.info("Orchestration complete for executionId={}, verdict={}", taskEvent.getExecutionId(), finalVerdict);
    }

    private List<TestCaseResultEvent> runStubExecution(List<TestCase> testCases) {
        if (testCases.isEmpty()) {
            return List.of(TestCaseResultEvent.builder()
                    .testCaseId(0L)
                    .verdict(Verdict.ACCEPTED)
                    .actualOutput(STUB_OUTPUT)
                    .expectedOutput(STUB_OUTPUT)
                    .runtimeMs(1)
                    .memoryBytes(1024)
                    .build());
        }
        return testCases.stream()
                .map(tc -> TestCaseResultEvent.builder()
                        .testCaseId(tc.getId())
                        .verdict(Verdict.ACCEPTED)
                        .actualOutput(STUB_OUTPUT)
                        .expectedOutput(tc.getExpectedOutput())
                        .runtimeMs(1)
                        .memoryBytes(1024)
                        .build())
                .toList();
    }

    private Verdict aggregateVerdict(List<TestCaseResultEvent> results) {
        if (results.stream().anyMatch(r -> r.getVerdict() == Verdict.COMPILE_ERROR)) {
            return Verdict.COMPILE_ERROR;
        }
        if (results.stream().anyMatch(r -> r.getVerdict() == Verdict.TIME_LIMIT_EXCEEDED)) {
            return Verdict.TIME_LIMIT_EXCEEDED;
        }
        if (results.stream().anyMatch(r -> r.getVerdict() == Verdict.RUNTIME_ERROR)) {
            return Verdict.RUNTIME_ERROR;
        }
        if (results.stream().allMatch(r -> r.getVerdict() == Verdict.ACCEPTED)) {
            return Verdict.ACCEPTED;
        }
        return Verdict.WRONG_ANSWER;
    }

    private int calculateScore(List<TestCaseResultEvent> results) {
        if (results.isEmpty()) return 0;
        long passed = results.stream().filter(r -> r.getVerdict() == Verdict.ACCEPTED).count();
        return (int) ((passed * 100) / results.size());
    }
}

