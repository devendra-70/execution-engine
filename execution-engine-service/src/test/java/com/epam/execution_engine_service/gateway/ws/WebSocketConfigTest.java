package com.epam.execution_engine_service.gateway.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketConfig
 * Tests WebSocket configuration including message broker setup,
 * STOMP endpoint registration, and authentication interceptor registration.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Mock
    private MessageBrokerRegistry messageBrokerRegistry;

    @Mock
    private StompEndpointRegistry stompEndpointRegistry;

    @Mock
    private StompWebSocketEndpointRegistration endpointRegistration;

    @Mock
    private ChannelRegistration channelRegistration;

    @InjectMocks
    private WebSocketConfig webSocketConfig;

    @BeforeEach
    void setUp() {
        when(stompEndpointRegistry.addEndpoint(anyString())).thenReturn(endpointRegistration);
        when(endpointRegistration.setAllowedOriginPatterns(anyString())).thenReturn(endpointRegistration);
    }

    @Test
    void configureMessageBroker_validRegistry_enablesSimpleBrokerAndSetsDestinationPrefixes() {
        // When
        webSocketConfig.configureMessageBroker(messageBrokerRegistry);

        // Then
        verify(messageBrokerRegistry, times(1)).enableSimpleBroker("/queue", "/topic");
        verify(messageBrokerRegistry, times(1)).setApplicationDestinationPrefixes("/app");
        verify(messageBrokerRegistry, times(1)).setUserDestinationPrefix("/user");
    }

    @Test
    void registerStompEndpoints_validRegistry_registersWebSocketEndpointWithSockJS() {
        // When
        webSocketConfig.registerStompEndpoints(stompEndpointRegistry);

        // Then
        verify(stompEndpointRegistry, times(1)).addEndpoint("/ws");
        verify(endpointRegistration, times(1)).setAllowedOriginPatterns("*");
        verify(endpointRegistration, times(1)).withSockJS();
    }

    @Test
    void configureClientInboundChannel_validRegistration_registersAuthInterceptor() {
        // When
        webSocketConfig.configureClientInboundChannel(channelRegistration);

        // Then
        verify(channelRegistration, times(1)).interceptors(webSocketAuthInterceptor);
    }

    @Test
    void configureMessageBroker_multipleInvocations_configuresCorrectly() {
        // When
        webSocketConfig.configureMessageBroker(messageBrokerRegistry);
        webSocketConfig.configureMessageBroker(messageBrokerRegistry);

        // Then
        verify(messageBrokerRegistry, times(2)).enableSimpleBroker("/queue", "/topic");
        verify(messageBrokerRegistry, times(2)).setApplicationDestinationPrefixes("/app");
        verify(messageBrokerRegistry, times(2)).setUserDestinationPrefix("/user");
    }

    @Test
    void registerStompEndpoints_multipleInvocations_registersCorrectly() {
        // When
        webSocketConfig.registerStompEndpoints(stompEndpointRegistry);
        webSocketConfig.registerStompEndpoints(stompEndpointRegistry);

        // Then
        verify(stompEndpointRegistry, times(2)).addEndpoint("/ws");
        verify(endpointRegistration, times(2)).setAllowedOriginPatterns("*");
        verify(endpointRegistration, times(2)).withSockJS();
    }
}

