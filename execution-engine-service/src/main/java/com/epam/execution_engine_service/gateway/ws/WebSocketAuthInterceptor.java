package com.epam.execution_engine_service.gateway.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.gateway.security.JwtTokenValidator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Intercepts STOMP CONNECT frames and authenticates the user via JWT.
 * This sets the session's Principal so that convertAndSendToUser() can route
 * messages to the correct WebSocket session.
 *
 * Client should send:
 *   CONNECT
 *   Authorization: Bearer <jwt>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenValidator jwtTokenValidator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token != null) {
                jwtTokenValidator.validateAndExtract(token).ifPresentOrElse(claims -> {
                    Object userIdRaw = claims.get("userId");
                    String principal = (userIdRaw instanceof Number number)
                            ? String.valueOf(number.longValue())
                            : claims.getSubject();
                    if (principal != null) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        accessor.setUser(auth);
                        log.debug("WebSocket CONNECT authenticated for userId={}", principal);
                    }
                }, () -> log.warn("WebSocket CONNECT received invalid JWT"));
            } else {
                log.warn("WebSocket CONNECT received without token — user destination routing disabled");
            }
        }
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // Try Authorization header in STOMP frame
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        // Try token header directly
        String token = accessor.getFirstNativeHeader("token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        return null;
    }
}


