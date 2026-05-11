package com.epam.execution_engine_service.orchestrator;

import com.epam.execution_engine_service.persistence.entity.ExecutionTaskEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExecutionTaskEventListener} (Pool A).
 *
 * <p>EPMICMPCOD-516 — Verifies the critical SRS §4.1 / Business Rule #3 invariants:
 * <ul>
 *   <li>Offset IS acknowledged ({@code ack.acknowledge()}) when Pool B succeeds.</li>
 *   <li>Offset is NOT acknowledged when Pool B throws any exception.</li>
 *   <li>Exceptions from Pool B are re-thrown so the error handler can seek back.</li>
 *   <li>Pool B ({@link ExecutionOrchestrator}) is invoked with the task event but
 *       NEVER with the {@link Acknowledgment} handle.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionTaskEventListener (Pool A) — Ack / No-Ack invariants")
class ExecutionTaskEventListenerUnitTest {

    @Mock
    private ExecutionOrchestrator executionOrchestrator;

    @Mock
    private Acknowledgment acknowledgment;

    @Mock
    private Executor executionTaskExecutor;

    private ExecutionTaskEventListener listener;

    private ExecutionTaskEvent sampleEvent;

    @BeforeEach
    void setUp() {
        // Simulate Pool B executor running tasks synchronously on the test thread.
        // This satisfies CompletableFuture.runAsync(..., executionTaskExecutor) in the listener.
        lenient().doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(executionTaskExecutor).execute(any(Runnable.class));

        // Construct listener explicitly to wire the @Qualifier-annotated executor
        listener = new ExecutionTaskEventListener(executionOrchestrator, executionTaskExecutor);

        sampleEvent = ExecutionTaskEvent.builder()
                .executionId("550e8400-e29b-41d4-a716-446655440000")
                .userId("user-1")
                .problemId("problem-42")
                .language("JAVA")
                .mode("SUBMIT")
                .sourceCode("public class Solution {}")
                .submittedAtMs(System.currentTimeMillis())
                .build();
    }

    @Nested
    @DisplayName("Successful orchestration path")
    class SuccessPath {

        @Test
        @DisplayName("ack.acknowledge() MUST be called when Pool B returns normally")
        void shouldAcknowledgeOffsetOnSuccess() {
            // Pool B returns successfully (no exception)
            doNothing().when(executionOrchestrator).orchestrateExecution(any(ExecutionTaskEvent.class));

            listener.consumeExecutionTask(sampleEvent, acknowledgment);

            // SRS §5.2 — Pool A commits offset after Pool B succeeds
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("Pool B must be invoked with the task event")
        void shouldInvokeOrchestratorWithTaskEvent() {
            doNothing().when(executionOrchestrator).orchestrateExecution(any(ExecutionTaskEvent.class));

            listener.consumeExecutionTask(sampleEvent, acknowledgment);

            verify(executionOrchestrator, times(1)).orchestrateExecution(sampleEvent);
        }

        @Test
        @DisplayName("Acknowledgment handle must NEVER be passed to Pool B")
        void shouldNeverPassAcknowledgmentToPoolB() {
            doNothing().when(executionOrchestrator).orchestrateExecution(any(ExecutionTaskEvent.class));

            listener.consumeExecutionTask(sampleEvent, acknowledgment);

            // Verify orchestrateExecution is called with event only (no Acknowledgment parameter)
            // The method signature itself enforces this — this test documents the contract
            verify(executionOrchestrator).orchestrateExecution(sampleEvent);
            verifyNoMoreInteractions(executionOrchestrator);
        }
    }

    @Nested
    @DisplayName("Failure path — offset NOT acknowledged")
    class FailurePath {

        @Test
        @DisplayName("ack.acknowledge() must NOT be called when Pool B throws RuntimeException")
        void shouldNotAcknowledgeOnRuntimeException() {
            // Simulate DB failure / container failure from Pool B
            doThrow(new RuntimeException("DB connection refused"))
                    .when(executionOrchestrator).orchestrateExecution(any(ExecutionTaskEvent.class));

            assertThatThrownBy(() -> listener.consumeExecutionTask(sampleEvent, acknowledgment))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB connection refused");

            // SRS §10 — offset NOT committed; error handler will seek back and redeliver
            verify(acknowledgment, never()).acknowledge();
        }

        @Test
        @DisplayName("ack.acknowledge() must NOT be called when Pool B throws a checked exception (wrapped)")
        void shouldNotAcknowledgeOnCheckedException() {
            doThrow(new RuntimeException("Persistence failure", new IllegalStateException("tx rolled back")))
                    .when(executionOrchestrator).orchestrateExecution(any(ExecutionTaskEvent.class));

            assertThatThrownBy(() -> listener.consumeExecutionTask(sampleEvent, acknowledgment))
                    .isInstanceOf(RuntimeException.class);

            verify(acknowledgment, never()).acknowledge();
        }

        @Test
        @DisplayName("Exception from Pool B must be re-thrown so the error handler can seek back")
        void shouldRethrowExceptionForErrorHandler() {
            RuntimeException poolBException = new RuntimeException("Sandbox unavailable");
            doThrow(poolBException)
                    .when(executionOrchestrator).orchestrateExecution(any(ExecutionTaskEvent.class));

            assertThatThrownBy(() -> listener.consumeExecutionTask(sampleEvent, acknowledgment))
                    .isSameAs(poolBException);
        }

        @Test
        @DisplayName("Pool B is still invoked even when acknowledgement is withheld")
        void shouldInvokeOrchestratorBeforeDecidingOnAck() {
            doThrow(new RuntimeException("Pool B failure"))
                    .when(executionOrchestrator).orchestrateExecution(any(ExecutionTaskEvent.class));

            try {
                listener.consumeExecutionTask(sampleEvent, acknowledgment);
            } catch (RuntimeException ignored) {
                // expected
            }

            // Pool B was called
            verify(executionOrchestrator, times(1)).orchestrateExecution(sampleEvent);
            // Offset NOT acknowledged
            verify(acknowledgment, never()).acknowledge();
        }
    }
}
