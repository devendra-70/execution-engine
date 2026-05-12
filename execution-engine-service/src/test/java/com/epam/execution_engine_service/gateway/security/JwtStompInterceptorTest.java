package com.epam.execution_engine_service.gateway.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtStompInterceptor}.
 *
 * Verifies JWT validation on STOMP CONNECT frames and pass-through for other frames.
 *
 * EPMICMPCOD-342 / Sub-task EPMICMPCOD-545
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtStompInterceptor — STOMP CONNECT JWT validation")
class JwtStompInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtClaimsExtractor jwtClaimsExtractor;

    @InjectMocks
    private JwtStompInterceptor interceptor;

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Message<byte[]> buildConnectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        // createMessage() preserves MutableMessageHeaders so MessageHeaderAccessor.getAccessor() works
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> buildNonConnectMessage(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test cases
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CONNECT frame — valid Bearer JWT")
    class ValidJwtConnect {

        @Test
        @DisplayName("Valid Bearer JWT binds userId as principal and passes message through")
        void preSend_validBearerJwt_bindsUserIdAsPrincipalAndReturnsMessage() {
            Claims mockClaims = mock(Claims.class);
            when(jwtTokenProvider.validateAndExtractClaims("valid.token.here")).thenReturn(mockClaims);
            when(jwtClaimsExtractor.extractUserId(mockClaims)).thenReturn("user-42");

            Message<byte[]> message = buildConnectMessage("Bearer valid.token.here");
            MessageChannel channel = mock(MessageChannel.class);

            Message<?> result = interceptor.preSend(message, channel);

            assertThat(result).isNotNull();
            Object userHeader = result.getHeaders().get("simpUser");
            assertThat(userHeader).isInstanceOf(UsernamePasswordAuthenticationToken.class);
            UsernamePasswordAuthenticationToken principal = (UsernamePasswordAuthenticationToken) userHeader;
            assertThat(principal.getName()).isEqualTo("user-42");
        }
    }

    @Nested
    @DisplayName("CONNECT frame — invalid JWT")
    class InvalidJwtConnect {

        @Test
        @DisplayName("Invalid JWT in STOMP CONNECT headers throws MessageDeliveryException")
        void preSend_invalidJwt_throwsMessageDeliveryException() {
            when(jwtTokenProvider.validateAndExtractClaims(anyString()))
                    .thenThrow(new RuntimeException("Invalid JWT token: signature mismatch"));

            Message<byte[]> message = buildConnectMessage("Bearer bad.token.value");
            MessageChannel channel = mock(MessageChannel.class);

            assertThatThrownBy(() -> interceptor.preSend(message, channel))
                    .isInstanceOf(MessageDeliveryException.class)
                    .hasMessageContaining("Authentication failed");

            verifyNoInteractions(jwtClaimsExtractor);
        }
    }

    @Nested
    @DisplayName("CONNECT frame — missing Authorization header")
    class MissingAuthorizationHeader {

        @Test
        @DisplayName("Missing Authorization header in STOMP CONNECT throws MessageDeliveryException")
        void preSend_missingAuthorizationHeader_throwsMessageDeliveryException() {
            Message<byte[]> message = buildConnectMessage(null);
            MessageChannel channel = mock(MessageChannel.class);

            assertThatThrownBy(() -> interceptor.preSend(message, channel))
                    .isInstanceOf(MessageDeliveryException.class)
                    .hasMessageContaining("Authorization header missing");

            verifyNoInteractions(jwtTokenProvider);
            verifyNoInteractions(jwtClaimsExtractor);
        }

        @Test
        @DisplayName("Authorization header without Bearer prefix throws MessageDeliveryException")
        void preSend_noBearerPrefix_throwsMessageDeliveryException() {
            Message<byte[]> message = buildConnectMessage("Basic dXNlcjpwYXNz");
            MessageChannel channel = mock(MessageChannel.class);

            assertThatThrownBy(() -> interceptor.preSend(message, channel))
                    .isInstanceOf(MessageDeliveryException.class);

            verifyNoInteractions(jwtTokenProvider);
        }
    }

    @Nested
    @DisplayName("Non-CONNECT STOMP frame — pass-through")
    class NonConnectFrame {

        @Test
        @DisplayName("SEND frame passes through unchanged without JWT validation")
        void preSend_sendFrame_passesWithoutJwtCheck() {
            Message<byte[]> message = buildNonConnectMessage(StompCommand.SEND);
            MessageChannel channel = mock(MessageChannel.class);

            Message<?> result = interceptor.preSend(message, channel);

            assertThat(result).isSameAs(message);
            verifyNoInteractions(jwtTokenProvider);
            verifyNoInteractions(jwtClaimsExtractor);
        }

        @Test
        @DisplayName("SUBSCRIBE frame passes through unchanged without JWT validation")
        void preSend_subscribeFrame_passesWithoutJwtCheck() {
            Message<byte[]> message = buildNonConnectMessage(StompCommand.SUBSCRIBE);
            MessageChannel channel = mock(MessageChannel.class);

            Message<?> result = interceptor.preSend(message, channel);

            assertThat(result).isSameAs(message);
            verifyNoInteractions(jwtTokenProvider);
            verifyNoInteractions(jwtClaimsExtractor);
        }
    }
}
