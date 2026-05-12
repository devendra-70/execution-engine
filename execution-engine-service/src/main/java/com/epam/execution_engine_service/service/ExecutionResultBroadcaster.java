package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.ExecutionResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * ExecutionResultBroadcaster — WebSocket result broadcasting (SRS §3.2, §14)
 * 
 * Broadcasts execution results to clients connected via STOMP/WebSocket.
 * Each client subscribes to /user/queue/execution-results to receive 
 * results specific to their submissions.
 * 
 * Flow (SRS Section 2.2, Step 14):
 * 1. ExecutionOrchestratorService completes execution
 * 2. PersistenceComponent saves to DB
 * 3. Result published to Redis Pub/Sub (execution-completed channel)
 * 4. ExecutionResultBroadcaster receives via Redis listener
 * 5. Broadcasts to connected WebSocket clients via STOMP
 * 
 * (SRS Section 2.2: "The specific Engine instance holding the WebSocket session
 *  pushes the result to the client.")
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionResultBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast execution result to client via WebSocket
     * 
     * Client receives result at STOMP endpoint: /user/queue/execution-results
     * 
     * @param userId User identifier (for targeted delivery)
     * @param result ExecutionResultEvent with verdict, score, test case results
     */
    public void broadcastResult(Long userId, ExecutionResultEvent result) {
        try {
            String destination = "/user/" + userId + "/queue/execution-results";
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/execution-results",
                    result
            );
            
            log.info("Result broadcasted to userId={}, executionId={}, verdict={}", 
                    userId, result.getExecutionId(), result.getVerdict());
        } catch (Exception e) {
            log.error("Failed to broadcast result to userId={}, executionId={}", 
                    userId, result.getExecutionId(), e);
        }
    }

    /**
     * Broadcast to all connected clients (e.g., for admin monitoring)
     * 
     * @param result ExecutionResultEvent
     */
    public void broadcastToAll(ExecutionResultEvent result) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/execution-completed",
                    result
            );
            
            log.debug("Result broadcasted to all subscribers: executionId={}", result.getExecutionId());
        } catch (Exception e) {
            log.error("Failed to broadcast to all subscribers: executionId={}", result.getExecutionId(), e);
        }
    }

}
