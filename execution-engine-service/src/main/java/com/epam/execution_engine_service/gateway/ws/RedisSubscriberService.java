package com.epam.execution_engine_service.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.domain.ExecutionResultEvent;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

/**
 * SRP: Responsible only for receiving Redis Pub/Sub messages and deserializing them.
 * DIP: Delegates WebSocket delivery to {@link WebSocketResultDelivery} — not STOMP directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriberService implements MessageListener {

    private final WebSocketResultDelivery wsDelivery;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter wsPushSent;
    private Counter wsPushFailed;

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, new ChannelTopic("execution-completed"));
        log.info("Subscribed to Redis Pub/Sub channel: execution-completed");

        wsPushSent   = Counter.builder("ws.push.sent")
                .description("Number of WebSocket result messages successfully pushed to users")
                .register(meterRegistry);
        wsPushFailed = Counter.builder("ws.push.failed")
                .description("Number of WebSocket result messages that failed to push")
                .register(meterRegistry);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            ExecutionResultEvent result = objectMapper.readValue(payload, ExecutionResultEvent.class);
            wsDelivery.deliver(result);
            wsPushSent.increment();
        } catch (Exception e) {
            wsPushFailed.increment();
            log.error("Error processing Redis Pub/Sub message", e);
        }
    }
}
