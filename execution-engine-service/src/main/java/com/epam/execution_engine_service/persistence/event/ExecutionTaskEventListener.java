package com.epam.execution_engine_service.persistence.event;

import com.epam.execution_engine_service.dto.ExecutionResultEvent;
import com.epam.execution_engine_service.dto.ExecutionTaskEvent;
import com.epam.execution_engine_service.service.ExecutionOrchestratorService;
import com.epam.execution_engine_service.service.ExecutionResultBroadcaster;
import com.epam.execution_engine_service.service.PersistenceService;
import com.epam.execution_engine_service.service.RedisExecutionStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ExecutionTaskEventListener — Kafka consumer handler (SRS §6, §14, §2.2)
 * 
 * Listens on Kafka topic "execution-tasks" and orchestrates execution.
 * Runs in Pool A (Kafka Listener thread pool).
 * 
 * Flow (SRS Section 2.2, Steps 6-14):
 * 1. Poll message from Kafka (Pool A, Step 7)
 * 2. Hand off to Pool B via ExecutionOrchestratorService (Step 11)
 * 3. Orchestrator executes and returns ExecutionResultEvent (Step 11-12)
 * 4. Persistence saves to DB (Step 12)
 * 5. Redis status updated to COMPLETED (Step 13)
 * 6. Result broadcasted to client via WebSocket (Step 14)
 * 7. Kafka offset committed ONLY after all success (Step 15)
 * 
 * Manual offset commit ensures offset advances only after DB success (SRS §5.2).
 * If any step fails, exception is caught and offset is NOT committed.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExecutionTaskEventListener {

    private final ExecutionOrchestratorService executionOrchestratorService;
    private final PersistenceService persistenceService;
    private final ExecutionResultBroadcaster resultBroadcaster;
    private final RedisExecutionStatusService redisStatusService;

    /**
     * Kafka listener for ExecutionTaskEvent (SRS §6)
     * 
     * Processes the complete execution pipeline:
     * 1. Execute code via sandbox (Pool B, ExecutionOrchestratorService)
     * 2. Persist results to PostgreSQL (transactional, batch insert)
     * 3. Update Redis status to COMPLETED
     * 4. Broadcast result to client via WebSocket
     * 5. Commit Kafka offset only after all success
     * 
     * @param event ExecutionTaskEvent from Kafka
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param ack Manual acknowledgment handle (SRS §5.2)
     */
    @KafkaListener(
            topics = "${app.kafka.topic:execution-tasks}",
            groupId = "${spring.kafka.consumer.group-id:execution-engine-group}",
            concurrency = "${app.kafka.concurrency:25}"
    )
    public void onExecutionTaskEvent(
            @Payload ExecutionTaskEvent event,
            @Header(name = "kafka_receivedPartitionId", required = false) int partition,
            @Header(name = "kafka_offset", required = false) long offset,
            Acknowledgment ack) {

        log.info("Received ExecutionTaskEvent: executionId={}, userId={}, problemId={}, partition={}, offset={}",
                event.getExecutionId(), event.getUserId(), event.getProblemId(), partition, offset);

        try {
            // Step 11: Orchestrate execution (Pool B via async delegation)
            log.debug("Starting orchestration: executionId={}, sourceCodeLength={}", 
                    event.getExecutionId(), event.getSourceCode() != null ? event.getSourceCode().length() : 0);
            ExecutionResultEvent result = executionOrchestratorService.execute(event);
            
            // C2 - Null check: Defensive programming against null orchestration result
            if (result == null) {
                log.error("Orchestrator returned null result: executionId={}, sourceCodeLength={}", 
                        event.getExecutionId(), event.getSourceCode() != null ? event.getSourceCode().length() : 0);
                throw new IllegalStateException("Execution orchestrator produced no result for executionId: " + event.getExecutionId());
            }
            
            log.info("Orchestration completed: executionId={}, verdict={}, score={}, testCaseCount={}", 
                    event.getExecutionId(), result.getVerdict(), result.getScore(), 
                    result.getTestCaseResults() != null ? result.getTestCaseResults().size() : 0);

            // Step 12: Persist to database (transactional)
            log.debug("Persisting execution result to database: executionId={}, verdict={}", 
                    event.getExecutionId(), result.getVerdict());
            persistenceService.persistExecutionResult(result);
            
            log.debug("Result persisted to database: executionId={}, persistenceOK=true",
                    event.getExecutionId());

            // Step 13: Update Redis status to COMPLETED
            log.debug("Updating Redis status to COMPLETED: executionId={}", event.getExecutionId());
            redisStatusService.setStatus(event.getExecutionId(), "COMPLETED");
            log.debug("Redis status updated: executionId={}, redisOK=true", event.getExecutionId());

            // Step 14: Broadcast result to client via WebSocket (non-blocking, handled in orchestrator)
            // This is already done by ExecutionOrchestratorService, but can also be done here explicitly
            log.debug("Broadcasting result to client: executionId={}, userId={}", 
                    event.getExecutionId(), event.getUserId());
            resultBroadcaster.broadcastResult(event.getUserId(), result);
            log.debug("Result broadcasted: executionId={}, broadcastOK=true", event.getExecutionId());

            // Step 15: Commit Kafka offset ONLY after all success (Manual offset commit gate)
            log.debug("All pipeline steps successful. Ready to commit offset: executionId={}, persistenceOK=true, redisOK=true, broadcastOK=true", 
                    event.getExecutionId());
            if (ack != null) {
                ack.acknowledge();
                log.info("Kafka offset committed: partition={}, offset={}, executionId={}", 
                        partition, offset, event.getExecutionId());
            }

        } catch (Exception e) {
            // DO NOT commit offset on error
            // Message remains in topic for retry (Kafka consumer group rebalance)
            log.error("ExecutionTaskEvent processing failed: executionId={}, partition={}, offset={}, errorType={}, errorMsg={}",
                    event.getExecutionId(), partition, offset, e.getClass().getSimpleName(), e.getMessage(), e);
            
            log.debug("Pipeline gate status on error: persistenceOK=false, redisOK=unknown, broadcastOK=false, offsetCommitOK=false");
            
            // Update Redis status to FAILED for visibility
            try {
                log.debug("Attempting to set Redis status to FAILED: executionId={}", event.getExecutionId());
                redisStatusService.setStatus(event.getExecutionId(), "FAILED");
                log.debug("Redis status set to FAILED: executionId={}", event.getExecutionId());
            } catch (Exception redisEx) {
                log.warn("Failed to update Redis status to FAILED: executionId={}, redisError={}", 
                        event.getExecutionId(), redisEx.getMessage());
            }
            
            // Offset NOT committed - message will be retried
            log.info("Kafka offset NOT committed (will retry): partition={}, offset={}, executionId={}", 
                    partition, offset, event.getExecutionId());
        }
    }

}
