package com.epam.execution_engine_service.config;

import com.epam.execution_engine_service.gateway.security.JwtStompInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket configuration for the Execution Engine.
 *
 * Per SRS §3.2:
 * - WebSocket endpoint: /ws (STOMP protocol)
 * - Client subscribes to: /user/queue/execution-results
 *
 * Per SRS §3.3:
 * - /ws HTTP upgrade handshake permitted without auth (handled in SecurityConfig)
 * - STOMP CONNECT frame validated via JwtStompInterceptor
 *
 * EPMICMPCOD-342 / Sub-task EPMICMPCOD-545
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompInterceptor jwtStompInterceptor;

    /**
     * Register the STOMP endpoint at /ws with SockJS fallback.
     * HTTP upgrade is permitted without Bearer JWT (SecurityConfig already permits /ws/**).
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Configure the simple in-memory message broker.
     * - /queue, /topic: broker destinations
     * - /app: application destination prefix for @MessageMapping
     * - /user: user destination prefix for SimpMessagingTemplate.convertAndSendToUser()
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Register JwtStompInterceptor on the CLIENT_INBOUND channel.
     * Intercepts STOMP CONNECT frames to validate JWT and bind userId as principal.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompInterceptor);
    }
}
