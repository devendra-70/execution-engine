package com.epam.execution_engine_service.orchestrator;

import com.epam.execution_engine_service.persistence.entity.ExecutionTaskEvent;
import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.epam.execution_engine_service.persistence.service.ExecutionResultPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

/**
 * Pool B — Execution Orchestrator.
 *
 * <p>SRS §4.1 — Thread Pool Architecture:
 * Pool B receives the execution task from Pool A, runs the user's code inside the
 * sandbox container via {@link ContainerSpawner}, aggregates the result into an
 * {@link ExecutionResultEvent}, and persists it via {@link ExecutionResultPersistenceService}.
 *
 * <p><strong>Business Rule #3 (SRS §4.1, EPMICMPCOD-353):</strong>
 * Pool B does NOT interact with Kafka offsets in any way.
 * The {@link org.springframework.kafka.support.Acknowledgment} handle is NEVER passed
 * to this class. Offset commitment is the exclusive responsibility of Pool A
 * ({@link ExecutionTaskEventListener}).
 *
 * <p>Persistence guarantee (SRS §5.2):
 * {@link ExecutionResultPersistenceService#persistExecutionResult(ExecutionResultEvent)} is
 * annotated {@code @Transactional(rollbackFor = Exception.class)}.  Any persistence failure
 * rolls back the transaction and propagates the exception to Pool A, which withholds the
 * Kafka offset acknowledgement so the message is redelivered.
 *
 * <p>Error flow (SRS §10):
 * Any exception thrown by this method propagates to {@link ExecutionTaskEventListener},
 * which re-throws it without calling {@code acknowledgment.acknowledge()}.
 * The {@link org.springframework.kafka.listener.DefaultErrorHandler} then seeks the
 * consumer back to the failed offset.
 *
 * <p>EPMICMPCOD-514: Orchestrate execution and trigger DB persistence (Pool B, no ack).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionOrchestrator {

    /**
     * Configurable sandbox execution timeout sourced from {@code app.execution.timeout-ms}
     * per SRS §12. Default: 3000 ms (3 s). Injected via Spring {@code @Value}.
     */
    @Value("${app.execution.timeout-ms:3000}")
    private long executionTimeoutMs;

    private final ContainerSpawner containerSpawner;
    private final ExecutionResultPersistenceService persistenceService;

    /**
     * Pool B entry point.  Executes the user's code in a sandbox container, maps the
     * container output to an {@link ExecutionResultEvent}, and persists the result.
     *
     * <p>Offset acknowledgement flow:
     * <ul>
     *   <li>On success: returns normally → Pool A calls {@code ack.acknowledge()}.</li>
     *   <li>On failure: throws an exception → Pool A does NOT call {@code ack.acknowledge()}
     *       → error handler seeks back → message redelivered (SRS §10).</li>
     * </ul>
     *
     * <p><strong>This method NEVER receives or touches the Kafka {@code Acknowledgment}
     * handle.</strong> That handle lives exclusively in Pool A ({@link ExecutionTaskEventListener}).
     *
     * @param event the {@link ExecutionTaskEvent} forwarded from Pool A (no Acknowledgment)
     * @throws RuntimeException on container error or persistence failure;
     *                          causes Pool A to withhold the offset acknowledgement
     */
    public void orchestrateExecution(ExecutionTaskEvent event) {

        log.info("Pool B starting orchestration for executionId={}, language={}, mode={}",
                event.getExecutionId(), event.getLanguage(), event.getMode());

        // Step 1: Execute user code in sandbox container (SRS §4.1 — Pool B manages container acquisition)
        // Timeout sourced from app.execution.timeout-ms (SRS §12 — configurable TLE per submission)
        // Use ceiling division: 2999 ms → 3 s (not 2 s), so configured timeout is fully honoured
        int timeoutSeconds = (int) Math.ceil(executionTimeoutMs / 1000.0);
        if (timeoutSeconds < 1) timeoutSeconds = 3; // guard against misconfiguration (e.g. timeout-ms=0)
        ContainerSpawner.ContainerExecutionResult containerResult =
                containerSpawner.spawn(event.getSourceCode(), timeoutSeconds);

        log.debug("Container execution completed for executionId={}: success={}, timeout={}",
                event.getExecutionId(), containerResult.isSuccess(), containerResult.isTimeout());

        // Step 2: Build ExecutionResultEvent from task + container result (SRS §9 domain model)
        ExecutionResultEvent resultEvent = buildResultEvent(event, containerResult);

        // Step 3: Persist to PostgreSQL (SRS §5.1 — JPA only, no Kafka for persistence)
        // @Transactional — if this throws, Pool A will NOT acknowledge the offset (SRS §5.2, §10)
        persistenceService.persistExecutionResult(resultEvent);

        log.info("Pool B orchestration complete for executionId={}, verdict={}",
                event.getExecutionId(), resultEvent.getVerdict());
    }

    /**
     * Maps a {@link ExecutionTaskEvent} and a {@link ContainerSpawner.ContainerExecutionResult}
     * into a persistence-ready {@link ExecutionResultEvent}.
     *
     * <p>Verdict derivation (SRS §9 — domain model):
     * <ul>
     *   <li>Container timeout → {@code TIME_LIMIT_EXCEEDED}</li>
     *   <li>Container error (non-zero exit or spawn failure) → {@code RUNTIME_ERROR}</li>
     *   <li>Exit code 0 → {@code PASSED} (sandbox reports PASSED on stdout for SUBMIT mode)</li>
     * </ul>
     *
     * @param event           the source task event
     * @param containerResult the raw container execution result
     * @return a fully populated {@link ExecutionResultEvent} ready for persistence
     */
    private ExecutionResultEvent buildResultEvent(
            ExecutionTaskEvent event,
            ContainerSpawner.ContainerExecutionResult containerResult) {

        String verdict = deriveVerdict(containerResult);
        String status = containerResult.isSuccess() ? "COMPLETED" : "FAILED";

        OffsetDateTime submittedAt = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(event.getSubmittedAtMs()), ZoneOffset.UTC);
        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);

        return ExecutionResultEvent.builder()
                .executionId(UUID.fromString(event.getExecutionId()))
                .userId(event.getUserId())
                .problemId(event.getProblemId())
                .language(event.getLanguage())
                .mode(event.getMode())
                .verdict(verdict)
                .status(status)
                .rawOutput(containerResult.getOutput())
                .errorOutput(containerResult.getError())
                .submittedCode(event.getSourceCode())
                .submittedAt(submittedAt)
                .completedAt(completedAt)
                // TODO: EPMICMPCOD-??? — populate from Sandbox Wrapper test-case results per SRS §4.3 (Execution Loop)
                .testResults(Collections.emptyList())
                .build();
    }

    /**
     * Derives the execution verdict from the raw container result.
     *
     * @param result raw container execution result
     * @return SRS §9 verdict string
     */
    private String deriveVerdict(ContainerSpawner.ContainerExecutionResult result) {
        if (result.isTimeout()) {
            return "TIME_LIMIT_EXCEEDED";
        }
        if (!result.isSuccess() || result.getExitCode() != 0) {
            return "RUNTIME_ERROR";
        }
        return "PASSED";
    }
}
