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
 * DRY/SRP: User-ID extraction from claims is delegated to
 * {@link JwtTokenValidator#extractUserId(String)} — no duplicated claim-parsing logic.
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
                jwtTokenValidator.extractUserId(token).ifPresentOrElse(userId -> {
                    String principal = String.valueOf(userId);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    accessor.setUser(auth);
                    log.debug("WebSocket CONNECT authenticated for userId={}", principal);
                }, () -> log.warn("WebSocket CONNECT received invalid JWT"));
            } else {
                log.warn("WebSocket CONNECT received without token — user destination routing disabled");
            }
        }
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        String token = accessor.getFirstNativeHeader("token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        return null;
    }
}
