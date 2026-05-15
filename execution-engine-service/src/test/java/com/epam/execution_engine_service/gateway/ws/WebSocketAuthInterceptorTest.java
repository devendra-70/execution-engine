package com.epam.execution_engine_service.gateway.ws;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.codeval.execution.gateway.security.JwtTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    private StompHeaderAccessor accessor;
    private Message<?> message;

    @BeforeEach
    void setUp() {
        accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSend_validTokenWithUserIdNumber_authenticatesAndSetsPrincipal() {
        // Given
        String token = "valid.jwt.token";
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Claims claims = new DefaultClaims();
        claims.put("userId", 12345L);
        claims.setSubject("user@example.com");

        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        // Recreate message with updated headers
        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Claims claims = new DefaultClaims();
        claims.put("userId", "user123");
        claims.setSubject("user@example.com");

        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Claims claims = new DefaultClaims();
        claims.setSubject("user@example.com");

        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        accessor.addNativeHeader("token", token);

        Claims claims = new DefaultClaims();
        claims.put("userId", 67890L);
        claims.setSubject("user@example.com");

        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.empty());

        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        accessor.addNativeHeader("token", "   ");
        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.addNativeHeader("Authorization", "Bearer valid.token");
        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, never()).validateAndExtract(anyString());
    }

    @Test
    void preSend_subscribeCommand_skipsAuthentication() {
        // Given
        accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.addNativeHeader("Authorization", "Bearer valid.token");
        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, never()).validateAndExtract(anyString());
    }

    @Test
    void preSend_disconnectCommand_skipsAuthentication() {
        // Given
        accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        accessor.addNativeHeader("Authorization", "InvalidPrefix token");
        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Claims claims = new DefaultClaims();
        // No userId and no subject

        when(jwtTokenValidator.validateAndExtract(token)).thenReturn(Optional.of(claims));

        message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // When
        Message<?> result = webSocketAuthInterceptor.preSend(message, messageChannel);

        // Then
        assertNotNull(result);
        verify(jwtTokenValidator, times(1)).validateAndExtract(token);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }
}

