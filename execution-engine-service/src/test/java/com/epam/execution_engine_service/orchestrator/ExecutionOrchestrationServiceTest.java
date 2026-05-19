package com.epam.execution_engine_service.orchestrator;

import com.epam.execution_engine_service.domain.*;
import com.epam.execution_engine_service.orchestrator.cache.TestCaseCacheService;
import com.epam.execution_engine_service.orchestrator.docker.DockerContainerPool;
import com.epam.execution_engine_service.orchestrator.docker.SandboxContainer;
import com.epam.execution_engine_service.persistence.service.PersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionOrchestrationServiceTest {

    @Mock private TestCaseCacheService testCaseCacheService;
    @Mock private DockerContainerPool containerPool;
    @Mock private PersistenceService persistenceService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ExecutionOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(orchestrationService, "timeoutMs", 3000L);
        ReflectionTestUtils.setField(orchestrationService, "statusTtlSeconds", 600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    // ── helpers ───────────────────────────────────────────────────────

    private ExecutionTaskEvent buildEvent() {
        return ExecutionTaskEvent.builder()
                .executionId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .userId(1L)
                .problemId(100L)
                .language("JAVA")
                .mode("SUBMIT")
                .sourceCode("public class Solution {}")
                .submittedAt(Instant.now())
                .build();
    }

    private TestCase buildTestCase(Long id) {
        return TestCase.builder().id(id).problemId(100L).input("1").expectedOutput("1").timeoutMs(1000).build();
    }

    private TestCaseResultEvent accepted(Long tcId) {
        return TestCaseResultEvent.builder().testCaseId(tcId).verdict(Verdict.ACCEPTED).runtimeMs(10).memoryBytes(512).build();
    }

    private TestCaseResultEvent withVerdict(Long tcId, Verdict verdict) {
        return TestCaseResultEvent.builder().testCaseId(tcId).verdict(verdict).runtimeMs(5).memoryBytes(256).build();
    }

    private SandboxContainer mockContainer(List<TestCaseResultEvent> results) throws Exception {
        SandboxContainer container = mock(SandboxContainer.class);
        when(container.getContainerId()).thenReturn("container-1");
        when(containerPool.acquire(anyLong())).thenReturn(container);
        when(container.execute(anyString(), anyList(), anyLong())).thenReturn(results);
        return container;
    }

    // ── stub-mode tests ───────────────────────────────────────────────

    @Test
    void orchestrate_dockerUnavailable_skipsContainerAndSaves() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(false);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L)));

        orchestrationService.orchestrate(buildEvent());

        verify(containerPool, never()).acquire(anyLong());
        verify(persistenceService, times(1)).saveSubmission(any(), any());
    }

    @Test
    void orchestrate_dockerUnavailableWithTestCases_stubReturnsAccepted() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(false);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(Verdict.ACCEPTED, cap.getValue().getVerdict());
        assertEquals(100, cap.getValue().getScore());
        assertEquals(2, cap.getValue().getTestCaseResults().size());
    }

    @Test
    void orchestrate_dockerAvailable_emptyTestCases_stubSingleResult() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of());

        orchestrationService.orchestrate(buildEvent());

        verify(containerPool, never()).acquire(anyLong());
        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(Verdict.ACCEPTED, cap.getValue().getVerdict());
        // Stub with empty list produces a single placeholder result
        assertEquals(1, cap.getValue().getTestCaseResults().size());
        assertEquals(0L, cap.getValue().getTestCaseResults().get(0).getTestCaseId());
    }

    // ── docker-mode tests ─────────────────────────────────────────────

    @Test
    void orchestrate_dockerAvailableWithTestCases_acquiresAndReleasesContainer() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L)));
        SandboxContainer container = mockContainer(List.of(accepted(1L)));

        orchestrationService.orchestrate(buildEvent());

        verify(containerPool).acquire(anyLong());
        verify(containerPool).release(container);
    }

    @Test
    void orchestrate_containerExecuteThrows_containerStillReleasedAndExceptionPropagates() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L)));
        SandboxContainer container = mock(SandboxContainer.class);
        when(container.getContainerId()).thenReturn("c1");
        when(containerPool.acquire(anyLong())).thenReturn(container);
        when(container.execute(anyString(), anyList(), anyLong())).thenThrow(new RuntimeException("socket error"));

        assertThrows(RuntimeException.class, () -> orchestrationService.orchestrate(buildEvent()));

        verify(containerPool).release(container);
        verify(persistenceService, never()).saveSubmission(any(), any());
    }

    @Test
    void orchestrate_acquireTimeoutMs_passedAsTimeoutPlusFive() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L)));
        mockContainer(List.of(accepted(1L)));

        orchestrationService.orchestrate(buildEvent());

        // timeoutMs=3000, so acquire should be called with 3000+5000 = 8000
        verify(containerPool).acquire(8000L);
    }

    // ── verdict aggregation tests ─────────────────────────────────────

    @Test
    void orchestrate_allAccepted_verdictIsAccepted() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(accepted(1L), accepted(2L)));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(Verdict.ACCEPTED, cap.getValue().getVerdict());
    }

    @Test
    void orchestrate_compileError_verdictIsCompileError() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(
                withVerdict(1L, Verdict.COMPILE_ERROR),
                withVerdict(2L, Verdict.WRONG_ANSWER)
        ));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(Verdict.COMPILE_ERROR, cap.getValue().getVerdict());
    }

    @Test
    void orchestrate_timeLimitExceeded_noCompileError_verdictIsTLE() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(
                withVerdict(1L, Verdict.TIME_LIMIT_EXCEEDED),
                withVerdict(2L, Verdict.WRONG_ANSWER)
        ));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, cap.getValue().getVerdict());
    }

    @Test
    void orchestrate_runtimeError_noTLEnoCompileError_verdictIsRuntimeError() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(
                accepted(1L),
                withVerdict(2L, Verdict.RUNTIME_ERROR)
        ));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(Verdict.RUNTIME_ERROR, cap.getValue().getVerdict());
    }

    @Test
    void orchestrate_mixedWrongAnswer_verdictIsWrongAnswer() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(
                accepted(1L),
                withVerdict(2L, Verdict.WRONG_ANSWER)
        ));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(Verdict.WRONG_ANSWER, cap.getValue().getVerdict());
    }

    @Test
    void orchestrate_compileErrorTakesPriorityOverTLE() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(
                withVerdict(1L, Verdict.TIME_LIMIT_EXCEEDED),
                withVerdict(2L, Verdict.COMPILE_ERROR)
        ));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(Verdict.COMPILE_ERROR, cap.getValue().getVerdict());
    }

    @Test
    void orchestrate_tleTakesPriorityOverRuntimeError() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(
                withVerdict(1L, Verdict.RUNTIME_ERROR),
                withVerdict(2L, Verdict.TIME_LIMIT_EXCEEDED)
        ));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, cap.getValue().getVerdict());
    }

    // ── score calculation tests ───────────────────────────────────────

    @Test
    void orchestrate_allPassed_score100() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(accepted(1L), accepted(2L)));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(100, cap.getValue().getScore());
    }

    @Test
    void orchestrate_halfPassed_score50() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(accepted(1L), withVerdict(2L, Verdict.WRONG_ANSWER)));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(50, cap.getValue().getScore());
    }

    @Test
    void orchestrate_nonePassed_score0() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(withVerdict(1L, Verdict.WRONG_ANSWER), withVerdict(2L, Verdict.WRONG_ANSWER)));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(0, cap.getValue().getScore());
    }

    // ── runtime, memory aggregation tests ────────────────────────────

    @Test
    void orchestrate_totalRuntimeIsSumOfAllTestCases() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(
                TestCaseResultEvent.builder().testCaseId(1L).verdict(Verdict.ACCEPTED).runtimeMs(30L).memoryBytes(512L).build(),
                TestCaseResultEvent.builder().testCaseId(2L).verdict(Verdict.ACCEPTED).runtimeMs(70L).memoryBytes(256L).build()
        ));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(100L, cap.getValue().getTotalRuntimeMs());
    }

    @Test
    void orchestrate_maxMemoryIsMaxAcrossTestCases() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(true);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L), buildTestCase(2L)));
        mockContainer(List.of(
                TestCaseResultEvent.builder().testCaseId(1L).verdict(Verdict.ACCEPTED).runtimeMs(10L).memoryBytes(1024L).build(),
                TestCaseResultEvent.builder().testCaseId(2L).verdict(Verdict.ACCEPTED).runtimeMs(20L).memoryBytes(2048L).build()
        ));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(2048L, cap.getValue().getMemoryBytes());
    }

    // ── Redis tests ───────────────────────────────────────────────────

    @Test
    void orchestrate_setsCompletedStatusInRedis() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(false);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L)));

        orchestrationService.orchestrate(buildEvent());

        verify(valueOperations).set(
                eq("execution:status:11111111-1111-1111-1111-111111111111"),
                eq("COMPLETED"),
                eq(Duration.ofSeconds(600L))
        );
    }

    @Test
    void orchestrate_publishesResultToRedisPubSub() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(false);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L)));

        orchestrationService.orchestrate(buildEvent());

        verify(redisTemplate).convertAndSend(eq("execution-completed"), anyString());
    }

    @Test
    void orchestrate_problemNameBuiltFromProblemId() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(false);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L)));

        orchestrationService.orchestrate(buildEvent());

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals("Problem 100", cap.getValue().getProblemName());
    }

    @Test
    void orchestrate_resultEventContainsExecutionIdAndUserId() throws Exception {
        when(containerPool.isDockerAvailable()).thenReturn(false);
        when(testCaseCacheService.getTestCases(100L)).thenReturn(List.of(buildTestCase(1L)));
        ExecutionTaskEvent event = buildEvent();

        orchestrationService.orchestrate(event);

        ArgumentCaptor<ExecutionResultEvent> cap = ArgumentCaptor.forClass(ExecutionResultEvent.class);
        verify(persistenceService).saveSubmission(any(), cap.capture());
        assertEquals(event.getExecutionId(), cap.getValue().getExecutionId());
        assertEquals(event.getUserId(), cap.getValue().getUserId());
        assertEquals(event.getProblemId(), cap.getValue().getProblemId());
    }
}
