package com.epam.execution_engine_service.orchestrator;

import com.epam.execution_engine_service.persistence.entity.ExecutionTaskEvent;
import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.epam.execution_engine_service.persistence.service.ExecutionResultPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExecutionOrchestrator} (Pool B).
 *
 * <p>EPMICMPCOD-516 — Verifies:
 * <ul>
 *   <li>Pool B invokes the sandbox container and persistence service on the success path.</li>
 *   <li>Pool B derives the correct verdict from the container result.</li>
 *   <li>Pool B re-throws persistence exceptions so Pool A withholds the Kafka offset ack.</li>
 *   <li>Pool B does NOT touch {@link org.springframework.kafka.support.Acknowledgment}
 *       in any scenario (structural enforcement — no Acknowledgment parameter on
 *       {@code orchestrateExecution}).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionOrchestrator (Pool B) — orchestration and persistence")
class ExecutionOrchestratorUnitTest {

    @Mock
    private ContainerSpawner containerSpawner;

    @Mock
    private ExecutionResultPersistenceService persistenceService;

    @InjectMocks
    private ExecutionOrchestrator orchestrator;

    private ExecutionTaskEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = ExecutionTaskEvent.builder()
                .executionId("550e8400-e29b-41d4-a716-446655440000")
                .userId("user-99")
                .problemId("problem-7")
                .language("PYTHON")
                .mode("SUBMIT")
                .sourceCode("print('hello')")
                .submittedAtMs(System.currentTimeMillis())
                .build();
    }

    @Nested
    @DisplayName("Success path — container passes")
    class SuccessPath {

        @Test
        @DisplayName("Calls persistence service with executionId from task event")
        void shouldPersistWithCorrectExecutionId() {
            ContainerSpawner.ContainerExecutionResult successResult =
                    ContainerSpawner.ContainerExecutionResult.success("output", 0);
            when(containerSpawner.spawn(any(), anyInt())).thenReturn(successResult);

            orchestrator.orchestrateExecution(sampleEvent);

            ArgumentCaptor<ExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(ExecutionResultEvent.class);
            verify(persistenceService).persistExecutionResult(captor.capture());

            ExecutionResultEvent persisted = captor.getValue();
            assertThat(persisted.getExecutionId().toString())
                    .isEqualTo(sampleEvent.getExecutionId());
        }

        @Test
        @DisplayName("Verdict is PASSED when container exits with code 0")
        void shouldDeriveVerdictPassedForZeroExitCode() {
            ContainerSpawner.ContainerExecutionResult successResult =
                    ContainerSpawner.ContainerExecutionResult.success("All tests passed", 0);
            when(containerSpawner.spawn(any(), anyInt())).thenReturn(successResult);

            orchestrator.orchestrateExecution(sampleEvent);

            ArgumentCaptor<ExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(ExecutionResultEvent.class);
            verify(persistenceService).persistExecutionResult(captor.capture());
            assertThat(captor.getValue().getVerdict()).isEqualTo("PASSED");
        }

        @Test
        @DisplayName("Status is COMPLETED when container exits with code 0")
        void shouldSetStatusCompletedForZeroExitCode() {
            when(containerSpawner.spawn(any(), anyInt()))
                    .thenReturn(ContainerSpawner.ContainerExecutionResult.success("ok", 0));

            orchestrator.orchestrateExecution(sampleEvent);

            ArgumentCaptor<ExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(ExecutionResultEvent.class);
            verify(persistenceService).persistExecutionResult(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("Persistence service is invoked exactly once per task")
        void shouldInvokePersistenceServiceExactlyOnce() {
            when(containerSpawner.spawn(any(), anyInt()))
                    .thenReturn(ContainerSpawner.ContainerExecutionResult.success("output", 0));

            orchestrator.orchestrateExecution(sampleEvent);

            verify(persistenceService, times(1)).persistExecutionResult(any());
        }

        @Test
        @DisplayName("Container spawner receives the source code from the task event")
        void shouldPassSourceCodeToContainerSpawner() {
            when(containerSpawner.spawn(any(), anyInt()))
                    .thenReturn(ContainerSpawner.ContainerExecutionResult.success("ok", 0));

            orchestrator.orchestrateExecution(sampleEvent);

            verify(containerSpawner).spawn(eq(sampleEvent.getSourceCode()), anyInt());
        }
    }

    @Nested
    @DisplayName("Failure path — container errors and timeouts")
    class ContainerFailurePath {

        @Test
        @DisplayName("Verdict is TIME_LIMIT_EXCEEDED on container timeout")
        void shouldDeriveVerdictTimeoutOnContainerTimeout() {
            when(containerSpawner.spawn(any(), anyInt()))
                    .thenReturn(ContainerSpawner.ContainerExecutionResult.timeout("partial output", 3));

            orchestrator.orchestrateExecution(sampleEvent);

            ArgumentCaptor<ExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(ExecutionResultEvent.class);
            verify(persistenceService).persistExecutionResult(captor.capture());
            assertThat(captor.getValue().getVerdict()).isEqualTo("TIME_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("Verdict is RUNTIME_ERROR on container spawn error")
        void shouldDeriveVerdictRuntimeErrorOnSpawnFailure() {
            when(containerSpawner.spawn(any(), anyInt()))
                    .thenReturn(ContainerSpawner.ContainerExecutionResult.error("Docker daemon unavailable"));

            orchestrator.orchestrateExecution(sampleEvent);

            ArgumentCaptor<ExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(ExecutionResultEvent.class);
            verify(persistenceService).persistExecutionResult(captor.capture());
            assertThat(captor.getValue().getVerdict()).isEqualTo("RUNTIME_ERROR");
        }

        @Test
        @DisplayName("Verdict is RUNTIME_ERROR on non-zero container exit code")
        void shouldDeriveVerdictRuntimeErrorOnNonZeroExitCode() {
            when(containerSpawner.spawn(any(), anyInt()))
                    .thenReturn(ContainerSpawner.ContainerExecutionResult.success("", 1));

            orchestrator.orchestrateExecution(sampleEvent);

            ArgumentCaptor<ExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(ExecutionResultEvent.class);
            verify(persistenceService).persistExecutionResult(captor.capture());
            assertThat(captor.getValue().getVerdict()).isEqualTo("RUNTIME_ERROR");
        }
    }

    @Nested
    @DisplayName("Persistence failure — exception must propagate to Pool A")
    class PersistenceFailurePath {

        @Test
        @DisplayName("Exception from persistence service propagates to Pool A (SRS §10 — no ack)")
        void shouldPropagatePersistenceException() {
            when(containerSpawner.spawn(any(), anyInt()))
                    .thenReturn(ContainerSpawner.ContainerExecutionResult.success("ok", 0));
            doThrow(new RuntimeException("PostgreSQL unreachable"))
                    .when(persistenceService).persistExecutionResult(any());

            // Exception propagates → Pool A will withhold ack → message redelivered (SRS §10)
            assertThatThrownBy(() -> orchestrator.orchestrateExecution(sampleEvent))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("PostgreSQL unreachable");
        }

        @Test
        @DisplayName("Container spawner is still called before persistence exception surfaces")
        void shouldCallContainerBeforePersistenceException() {
            when(containerSpawner.spawn(any(), anyInt()))
                    .thenReturn(ContainerSpawner.ContainerExecutionResult.success("ok", 0));
            doThrow(new RuntimeException("DB error"))
                    .when(persistenceService).persistExecutionResult(any());

            try {
                orchestrator.orchestrateExecution(sampleEvent);
            } catch (RuntimeException ignored) {
                // expected
            }

            verify(containerSpawner, times(1)).spawn(any(), anyInt());
            verify(persistenceService, times(1)).persistExecutionResult(any());
        }
    }
}
