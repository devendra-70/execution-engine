package com.epam.execution_engine_service.gateway.websocket;

import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub message listener for the {@code execution-completed} channel.
 *
 * Per SRS §2.2 Step 14 and §8:
 * Every ECS instance subscribes to {@code execution-completed}. On receiving a
 * broadcast, this listener checks whether the target {@code userId} has an active
 * STOMP session on THIS instance via {@link SimpUserRegistry}. If yes, it delivers
 * the result to {@code /user/queue/execution-results} via
 * {@link SimpMessagingTemplate#convertAndSendToUser}. If no session is found,
 * the message is silently discarded (DEBUG log only) — guaranteeing no duplicate
 * delivery across horizontally scaled instances.
 *
 * Wired into {@link org.springframework.data.redis.listener.RedisMessageListenerContainer}
 * in {@code RedisConfig} on the {@code ChannelTopic("execution-completed")} topic.
 *
 * EPMICMPCOD-342 / Sub-task EPMICMPCOD-546
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionResultMessageListener implements MessageListener {

    /** STOMP user destination for execution results (SRS §3.2). */
    static final String RESULT_DESTINATION = "/queue/execution-results";

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;
    private final ObjectMapper objectMapper;

    /**
     * Invoked by {@link org.springframework.data.redis.listener.RedisMessageListenerContainer}
     * each time a message is published to the {@code execution-completed} channel.
     *
     * @param message the raw Redis message (body = JSON-encoded {@link ExecutionResultEvent})
     * @param pattern the channel pattern (unused)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        ExecutionResultEvent event;

        try {
            event = objectMapper.readValue(body, ExecutionResultEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialise ExecutionResultEvent from execution-completed: {}", e.getMessage());
            return;
        }

        String userId = event.getUserId();
        if (userId == null) {
            log.warn("ExecutionResultEvent has null userId; discarding broadcast");
            return;
        }

        // Check if this instance holds an active STOMP session for the target user.
        if (simpUserRegistry.getUser(userId) != null) {
            log.debug("Dispatching execution result to STOMP session: userId={}, executionId={}",
                    userId, event.getExecutionId());
            messagingTemplate.convertAndSendToUser(userId, RESULT_DESTINATION, event);
        } else {
            // No STOMP session on this instance — silent discard (SRS §2.2 Step 14).
            log.debug("No active STOMP session for userId={}; discarding execution-completed broadcast", userId);
        }
    }
}
