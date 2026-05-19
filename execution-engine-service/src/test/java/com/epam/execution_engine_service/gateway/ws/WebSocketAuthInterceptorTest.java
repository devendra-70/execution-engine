package com.epam.execution_engine_service.gateway.ws;

import io.jsonwebtoken.Claims;
import com.epam.execution_engine_service.gateway.security.JwtTokenValidator;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketAuthInterceptor
 * Tests JWT authentication for WebSocket STOMP CONNECT frames,
 * including token extraction, validation, and user principal setup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    private Claims createClaims(String subject, Object userId) {
        Claims claims = mock(Claims.class);
        when(claims.get("userId")).thenReturn(userId);
        when(claims.getSubject()).thenReturn(subject);
        return claims;
    }

    /**
     * Helper method to create a CONNECT message with specified headers.
     * Ensures the StompHeaderAccessor is properly attached to the message.
     */
    private Message<?> createConnectMessageWithHeaders(String headerName, String headerValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader(headerName, headerValue);
        // setLeaveMutable(true) keeps the accessor mutable so that setUser() in the
        // interceptor can modify the headers and be visible via StompHeaderAccessor.wrap()
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /**
     * Helper method to create a CONNECT message without headers.
     */
    private Message<?> createConnectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /**
     * Helper method to create a message with a different STOMP command.
     */
    private Message<?> createMessageWithCommand(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSend_validTokenWithUserIdNumber_authenticatesAndSetsPrincipal() {
        // Given
        String token = "valid.jwt.token";
        Message<?> message = createConnectMessageWithHeaders("Authorization", "Bearer " + token);

        Claims claims = createClaims("user@example.com", 12345L);
        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, times(1)).validateAndExtract(token);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Principal principal = resultAccessor.getUser();
        assertNotNull(principal);
        assertTrue(principal instanceof UsernamePasswordAuthenticationToken);
        assertEquals("12345", principal.getName());
    }

    @Test
    void preSend_validTokenWithUserIdString_authenticatesAndSetsPrincipal() {
        // Given
        String token = "valid.jwt.token";
        Message<?> message = createConnectMessageWithHeaders("Authorization", "Bearer " + token);

        Claims claims = createClaims("user@example.com", "user123");
        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, times(1)).validateAndExtract(token);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Principal principal = resultAccessor.getUser();
        assertNotNull(principal);
        assertEquals("user@example.com", principal.getName());
    }

    @Test
    void preSend_validTokenWithSubjectOnly_authenticatesAndSetsPrincipalFromSubject() {
        // Given
        String token = "valid.jwt.token";
        Message<?> message = createConnectMessageWithHeaders("Authorization", "Bearer " + token);

        Claims claims = createClaims("user@example.com", null);
        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, times(1)).validateAndExtract(token);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Principal principal = resultAccessor.getUser();
        assertNotNull(principal);
        assertEquals("user@example.com", principal.getName());
    }

    @Test
    void preSend_validTokenDirectHeader_authenticatesSuccessfully() {
        // Given
        String token = "valid.jwt.token";
        Message<?> message = createConnectMessageWithHeaders("token", token);

        Claims claims = createClaims("user@example.com", 67890L);
        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, times(1)).validateAndExtract(token);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Principal principal = resultAccessor.getUser();
        assertNotNull(principal);
        assertEquals("67890", principal.getName());
    }

    @Test
    void preSend_invalidToken_doesNotSetPrincipal() {
        // Given
        String token = "invalid.jwt.token";
        Message<?> message = createConnectMessageWithHeaders("Authorization", "Bearer " + token);

        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.empty());

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, times(1)).validateAndExtract(token);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }

    @Test
    void preSend_noToken_doesNotSetPrincipal() {
        // Given - no token added
        Message<?> message = createConnectMessage();

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, never()).validateAndExtract(anyString());

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }

    @Test
    void preSend_blankTokenHeader_doesNotSetPrincipal() {
        // Given
        Message<?> message = createConnectMessageWithHeaders("token", "   ");

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, never()).validateAndExtract(anyString());

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }

    @Test
    void preSend_nonConnectCommand_skipsAuthentication() {
        // Given
        Message<?> message = createMessageWithCommand(StompCommand.SEND);

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, never()).validateAndExtract(anyString());
    }

    @Test
    void preSend_subscribeCommand_skipsAuthentication() {
        // Given
        Message<?> message = createMessageWithCommand(StompCommand.SUBSCRIBE);

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, never()).validateAndExtract(anyString());
    }

    @Test
    void preSend_disconnectCommand_skipsAuthentication() {
        // Given
        Message<?> message = createMessageWithCommand(StompCommand.DISCONNECT);

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, never()).validateAndExtract(anyString());
    }

    @Test
    void preSend_nullAccessor_returnsMessageUnchanged() {
        // Given
        Message<?> messageWithoutAccessor = MessageBuilder.withPayload(new byte[0]).build();

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(messageWithoutAccessor, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, never()).validateAndExtract(anyString());
    }

    @Test
    void preSend_tokenWithoutBearerPrefix_notExtracted() {
        // Given
        Message<?> message = createConnectMessageWithHeaders("Authorization", "InvalidPrefix token");

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, never()).validateAndExtract(anyString());
    }

    @Test
    void preSend_validTokenWithNullPrincipal_doesNotSetUser() {
        // Given
        String token = "valid.jwt.token";
        Message<?> message = createConnectMessageWithHeaders("Authorization", "Bearer " + token);

        Claims claims = createClaims(null, null);
        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, times(1)).validateAndExtract(token);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }
}

