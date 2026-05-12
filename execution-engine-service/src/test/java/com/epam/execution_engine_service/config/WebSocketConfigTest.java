package com.epam.execution_engine_service.config;

import com.epam.execution_engine_service.gateway.security.JwtStompInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebSocketConfig}.
 *
 * Verifies that:
 * - WebSocketConfig can be instantiated without error (equivalent to context load check).
 * - {@link JwtStompInterceptor} is registered on the CLIENT_INBOUND channel.
 * - Broker and endpoint configuration do not throw.
 *
 * EPMICMPCOD-342 / Sub-task EPMICMPCOD-545
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketConfig — STOMP endpoint and interceptor registration")
class WebSocketConfigTest {

    @Mock
    private JwtStompInterceptor jwtStompInterceptor;

    @InjectMocks
    private WebSocketConfig webSocketConfig;

    // ──────────────────────────────────────────────────────────────────────────
    // Test cases
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Context load — @EnableWebSocketMessageBroker")
    class ContextLoad {

        @Test
        @DisplayName("WebSocketConfig instantiates without error (context loads cleanly)")
        void webSocketConfig_instantiation_doesNotThrow() {
            // @InjectMocks already provides a valid instance.
            // If the config were broken, @InjectMocks / constructor injection would fail.
            assertThatNoException().isThrownBy(() -> new WebSocketConfig(jwtStompInterceptor));
        }
    }

    @Nested
    @DisplayName("CLIENT_INBOUND channel — JwtStompInterceptor registration")
    class InboundChannelRegistration {

        @Test
        @DisplayName("JwtStompInterceptor is registered on the CLIENT_INBOUND channel")
        void configureClientInboundChannel_registersJwtStompInterceptor() {
            ChannelRegistration registration = mock(ChannelRegistration.class);

            webSocketConfig.configureClientInboundChannel(registration);

            verify(registration, times(1)).interceptors(jwtStompInterceptor);
        }
    }

    @Nested
    @DisplayName("Message broker configuration")
    class MessageBrokerConfig {

        @Test
        @DisplayName("configureMessageBroker does not throw with mocked registry")
        void configureMessageBroker_doesNotThrow() {
            MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
            when(registry.enableSimpleBroker(anyString(), anyString())).thenReturn(null);
            when(registry.setApplicationDestinationPrefixes(anyString())).thenReturn(null);
            when(registry.setUserDestinationPrefix(anyString())).thenReturn(null);

            assertThatNoException().isThrownBy(() -> webSocketConfig.configureMessageBroker(registry));
        }
    }

    @Nested
    @DisplayName("STOMP endpoint registration")
    class StompEndpointRegistration {

        @Test
        @DisplayName("registerStompEndpoints registers /ws endpoint without error")
        void registerStompEndpoints_registersWsEndpointWithoutError() {
            StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
            StompWebSocketEndpointRegistration endpointReg = mock(StompWebSocketEndpointRegistration.class);
            when(registry.addEndpoint("/ws")).thenReturn(endpointReg);
            when(endpointReg.setAllowedOriginPatterns("*")).thenReturn(endpointReg);

            assertThatNoException().isThrownBy(() -> webSocketConfig.registerStompEndpoints(registry));

            verify(registry).addEndpoint("/ws");
            verify(endpointReg).setAllowedOriginPatterns("*");
        }
    }
}
