package com.epam.execution_engine_service.orchestrator;

import com.epam.execution_engine_service.persistence.entity.ExecutionTaskEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Pool A — Kafka Listener for execution-tasks topic.
 *
 * <p>SRS §4.1 — Thread Pool Architecture:
 * Pool A consumes {@link ExecutionTaskEvent} messages from the {@code execution-tasks} topic
 * and hands the task off to Pool B ({@link ExecutionOrchestrator}) for execution and
 * result persistence.  Only after Pool B returns successfully does Pool A commit the
 * Kafka offset by calling {@code acknowledgment.acknowledge()}.
 *
 * <p><strong>Business Rule #3 (SRS §4.1, EPMICMPCOD-353):</strong>
 * Offset acknowledgement is the sole responsibility of Pool A.
 * Pool B ({@link ExecutionOrchestrator}) does NOT interact with Kafka offsets directly.
 * The {@link Acknowledgment} handle is NEVER passed to Pool B.
 *
 * <p>Error flow (SRS §10):
 * If Pool B throws any exception (e.g. DB unreachable, transaction rolled back), Pool A
 * re-throws the exception without calling {@code acknowledgment.acknowledge()}.
 * The {@link org.springframework.kafka.listener.DefaultErrorHandler} configured in
 * {@link com.epam.execution_engine_service.config.KafkaConfig} then seeks the consumer
 * back to the failed offset, causing the message to be redelivered on the next poll.
 *
 * <p>EPMICMPCOD-513: Manual AckMode wired via {@code kafkaListenerContainerFactory}.
 * EPMICMPCOD-514: Pool A acknowledges after Pool B returns (no ack handle to Pool B).
 */
@Slf4j
@Component
public class ExecutionTaskEventListener {

    private final ExecutionOrchestrator executionOrchestrator;

    /**
     * Pool B executor — separate thread pool for container I/O (SRS §4.1).
     * Injected by name to avoid ambiguity with Spring Boot's application task executor.
     */
    private final Executor executionTaskExecutor;

    public ExecutionTaskEventListener(
            ExecutionOrchestrator executionOrchestrator,
            @Qualifier("executionTaskExecutor") Executor executionTaskExecutor) {
        this.executionOrchestrator = executionOrchestrator;
        this.executionTaskExecutor = executionTaskExecutor;
    }

    /**
     * Consumes an {@link ExecutionTaskEvent} from the {@code execution-tasks} topic (Pool A).
     *
     * <p>Execution flow:
     * <ol>
     *   <li>Pool A receives message + Acknowledgment handle.</li>
     *   <li>Pool A calls {@link ExecutionOrchestrator#orchestrateExecution(ExecutionTaskEvent)}
     *       — Pool B executes, persists result, and returns void.</li>
     *   <li>On success: Pool A calls {@code ack.acknowledge()} to commit the Kafka offset.</li>
     *   <li>On failure: Pool A re-throws the exception; offset NOT committed; error handler
     *       seeks back; message redelivered.</li>
     * </ol>
     *
     * <p><strong>The {@code ack} parameter is NEVER forwarded to Pool B.</strong>
     *
     * @param event the deserialized {@link ExecutionTaskEvent} from Kafka
     * @param ack   the Kafka {@link Acknowledgment} handle — owned exclusively by Pool A
     * @throws RuntimeException propagated from Pool B on execution or persistence failure
     */
    @KafkaListener(
            topics = "${app.kafka.topic}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeExecutionTask(
            ExecutionTaskEvent event,
            Acknowledgment ack) {

        log.info("Pool A received ExecutionTaskEvent: executionId={}, userId={}, mode={}",
                event.getExecutionId(), event.getUserId(), event.getMode());

        try {
            // Hand off to Pool B — submit to separate TaskExecutor (SRS §4.1 two-pool architecture).
            // Pool A blocks on the future so it can honour the ack-after-commit rule (SRS §5.2).
            // Pool B NEVER receives the Acknowledgment handle (SRS §4.1 BR#3).
            CompletableFuture.runAsync(
                    () -> executionOrchestrator.orchestrateExecution(event),
                    executionTaskExecutor
            ).get();

            // Pool A commits the Kafka offset ONLY after Pool B successfully persists result (SRS §5.2)
            ack.acknowledge();
            log.info("Pool A acknowledged Kafka offset for executionId={}", event.getExecutionId());

        } catch (ExecutionException e) {
            // Unwrap the exception from the CompletableFuture and re-throw as-is.
            // Pool A must NOT acknowledge — error handler will seek back and redeliver (SRS §10).
            Throwable cause = e.getCause();
            log.error("Pool B raised exception for executionId={}; offset NOT acknowledged — message will be redelivered",
                    event.getExecutionId(), cause);
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Pool A interrupted while waiting for Pool B; offset NOT acknowledged for executionId={}",
                    event.getExecutionId(), e);
            throw new RuntimeException("Pool A interrupted waiting for Pool B result", e);
        }
    }
}
