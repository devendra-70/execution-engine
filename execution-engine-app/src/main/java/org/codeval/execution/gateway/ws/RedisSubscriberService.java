package org.codeval.execution.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeval.execution.domain.ExecutionResultEvent;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriberService implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, new PatternTopic("execution-completed"));
        log.info("Subscribed to Redis Pub/Sub channel: execution-completed");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            ExecutionResultEvent result = objectMapper.readValue(payload, ExecutionResultEvent.class);

            // Push result to the specific user's WebSocket queue
            String destination = "/queue/execution-results";
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(result.getUserId()),
                    destination,
                    result
            );
            log.info("Pushed result for executionId {} to user {}", result.getExecutionId(), result.getUserId());
        } catch (Exception e) {
            log.error("Error processing Redis Pub/Sub message", e);
        }
    }
}

