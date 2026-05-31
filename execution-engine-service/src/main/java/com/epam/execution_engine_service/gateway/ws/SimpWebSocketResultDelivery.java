package com.epam.execution_engine_service.gateway.ws;

import com.epam.execution_engine_service.domain.ExecutionResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * SRP: Single responsibility — route an execution result to the correct user's WebSocket queue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimpWebSocketResultDelivery implements WebSocketResultDelivery {

    private static final String DESTINATION = "/queue/execution-results";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void deliver(ExecutionResultEvent result) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(result.getUserId()),
                DESTINATION,
                result
        );
        log.info("Pushed result for executionId={} to userId={}", result.getExecutionId(), result.getUserId());
    }
}

