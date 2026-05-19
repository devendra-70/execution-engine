package com.epam.execution_engine_service.orchestrator.kafka;

import com.epam.execution_engine_service.domain.ExecutionStatus;
import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import com.epam.execution_engine_service.orchestrator.ExecutionOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionTaskListenerTest {

    @Mock private ExecutionOrchestrationService orchestrationService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ThreadPoolTaskExecutor orchestrationPool;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private Acknowledgment acknowledgment;

    private ExecutionTaskListener listener;

    @BeforeEach
    void setUp() {
        listener = new ExecutionTaskListener(orchestrationService, redisTemplate, orchestrationPool);
        ReflectionTestUtils.setField(listener, "statusTtlSeconds", 600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Run submitted tasks synchronously so we can verify their behaviour
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(orchestrationPool).submit(any(Runnable.class));
    }

    private ExecutionTaskEvent buildEvent() {
        return ExecutionTaskEvent.builder()
                .executionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
                .userId(1L)
                .problemId(42L)
                .language("JAVA")
                .mode("SUBMIT")
                .sourceCode("class S{}")
                .submittedAt(Instant.now())
                .build();
    }

    @Test
    void onExecutionTask_setsRedisStatusToProcessing() {
        ExecutionTaskEvent event = buildEvent();

        listener.onExecutionTask(event, acknowledgment);

        verify(valueOperations).set(
                eq("execution:status:aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                eq(ExecutionStatus.PROCESSING.name()),
                eq(Duration.ofSeconds(600L))
        );
    }

    @Test
    void onExecutionTask_submitsTaskToOrchestrationPool() {
        ExecutionTaskEvent event = buildEvent();

        listener.onExecutionTask(event, acknowledgment);

        verify(orchestrationPool, times(1)).submit(any(Runnable.class));
    }

    @Test
    void onExecutionTask_orchestrationSucceeds_acknowledgesMessage() throws Exception {
        ExecutionTaskEvent event = buildEvent();
        doNothing().when(orchestrationService).orchestrate(event);

        listener.onExecutionTask(event, acknowledgment);

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void onExecutionTask_orchestrationSucceeds_callsOrchestrateWithEvent() throws Exception {
        ExecutionTaskEvent event = buildEvent();

        listener.onExecutionTask(event, acknowledgment);

        verify(orchestrationService, times(1)).orchestrate(event);
    }

    @Test
    void onExecutionTask_orchestrationFails_doesNotAcknowledge() throws Exception {
        ExecutionTaskEvent event = buildEvent();
        doThrow(new Exception("orchestration failed")).when(orchestrationService).orchestrate(any());

        listener.onExecutionTask(event, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onExecutionTask_orchestrationFails_redisStatusStillSetToProcessing() throws Exception {
        ExecutionTaskEvent event = buildEvent();
        doThrow(new RuntimeException("db failure")).when(orchestrationService).orchestrate(any());

        listener.onExecutionTask(event, acknowledgment);

        // Redis set to PROCESSING should still happen (it's outside the pool submission)
        verify(valueOperations).set(
                contains("execution:status:"),
                eq(ExecutionStatus.PROCESSING.name()),
                any(Duration.class)
        );
    }

    @Test
    void onExecutionTask_orchestrationFails_noExceptionPropagated() throws Exception {
        ExecutionTaskEvent event = buildEvent();
        doThrow(new RuntimeException("crash")).when(orchestrationService).orchestrate(any());

        // The listener must NOT propagate the exception – it swallows it to avoid consumer crash
        assertDoesNotThrow(() -> listener.onExecutionTask(event, acknowledgment));
    }

    @Test
    void onExecutionTask_redisKeyContainsExecutionId() {
        ExecutionTaskEvent event = buildEvent();

        listener.onExecutionTask(event, acknowledgment);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), any(Duration.class));
        assertTrue(keyCaptor.getValue().contains(event.getExecutionId().toString()));
    }

    @Test
    void onExecutionTask_redisTtlMatchesConfiguredValue() {
        ExecutionTaskEvent event = buildEvent();

        listener.onExecutionTask(event, acknowledgment);

        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(600L)));
    }

    @Test
    void onExecutionTask_processingStatusSetBeforePoolSubmission() {
        // Verify ordering: Redis set must happen before pool submit
        ExecutionTaskEvent event = buildEvent();
        var order = inOrder(valueOperations, orchestrationPool);

        listener.onExecutionTask(event, acknowledgment);

        order.verify(valueOperations).set(anyString(), eq(ExecutionStatus.PROCESSING.name()), any(Duration.class));
        order.verify(orchestrationPool).submit(any(Runnable.class));
    }

    @Test
    void onExecutionTask_differentEvents_eachGetsOwnRedisKey() {
        ExecutionTaskEvent event1 = ExecutionTaskEvent.builder()
                .executionId(UUID.fromString("11111111-0000-0000-0000-000000000000"))
                .userId(1L).problemId(1L).language("JAVA").mode("RUN")
                .sourceCode("code").submittedAt(Instant.now()).build();
        ExecutionTaskEvent event2 = ExecutionTaskEvent.builder()
                .executionId(UUID.fromString("22222222-0000-0000-0000-000000000000"))
                .userId(2L).problemId(2L).language("PYTHON").mode("SUBMIT")
                .sourceCode("code2").submittedAt(Instant.now()).build();

        listener.onExecutionTask(event1, acknowledgment);
        listener.onExecutionTask(event2, acknowledgment);

        verify(valueOperations).set(eq("execution:status:11111111-0000-0000-0000-000000000000"),
                eq(ExecutionStatus.PROCESSING.name()), any(Duration.class));
        verify(valueOperations).set(eq("execution:status:22222222-0000-0000-0000-000000000000"),
                eq(ExecutionStatus.PROCESSING.name()), any(Duration.class));
        verify(acknowledgment, times(2)).acknowledge();
    }
}
