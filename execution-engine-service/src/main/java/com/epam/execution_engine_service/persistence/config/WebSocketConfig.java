package com.epam.execution_engine_service.persistence.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocketConfig — WebSocket/STOMP configuration (SRS §3.2)
 * 
 * Configures WebSocket endpoint and STOMP message broker:
 * - Endpoint: /ws
 * - Client receives results on: /user/queue/execution-results
 * - Server publishes to: /topic/execution-completed
 * 
 * Supports real-time delivery of execution results to connected clients.
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure STOMP endpoints
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();
    }

    /**
     * Configure message broker
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable in-memory message broker for /topic and /queue
        registry.enableSimpleBroker("/topic", "/queue");
        
        // User destinations prefix (for user-specific queues)
        registry.setUserDestinationPrefix("/user");
        
        // Application destination prefix (for @SendTo and @SendToUser)
        registry.setApplicationDestinationPrefixes("/app");
    }

}
