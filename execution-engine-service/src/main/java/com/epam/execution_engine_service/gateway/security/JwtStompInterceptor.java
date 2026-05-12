package com.epam.execution_engine_service.gateway.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * STOMP channel interceptor that validates Bearer JWT on CONNECT frames and binds
 * the authenticated userId as the STOMP session principal.
 *
 * Per SRS §3.1: simple JWT validation via shared secret (app.jwt.secret-key).
 * Per SRS §3.3: STOMP CONNECT carries Bearer JWT; /ws HTTP handshake is open.
 *
 * On a valid JWT: userId is set as UsernamePasswordAuthenticationToken principal.
 * Spring STOMP then registers it in SimpUserRegistry automatically.
 *
 * On an invalid or absent JWT: throws MessageDeliveryException, which causes
 * Spring to send a STOMP ERROR frame and reject the session.
 *
 * EPMICMPCOD-342 / Sub-task EPMICMPCOD-545
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtStompInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Only intercept CONNECT frames; pass all others through unchanged.
            return message;
        }

        String token = extractBearerToken(accessor);

        if (token == null) {
            log.warn("STOMP CONNECT rejected: no Bearer JWT found in CONNECT headers");
            throw new MessageDeliveryException(message,
                    "STOMP CONNECT rejected: Authorization header missing or not Bearer");
        }

        Claims claims;
        try {
            claims = jwtTokenProvider.validateAndExtractClaims(token);
        } catch (Exception e) {
            log.warn("STOMP CONNECT rejected: invalid JWT — {}", e.getMessage());
            throw new MessageDeliveryException(message,
                    "Authentication failed", e);
        }

        String userId = jwtClaimsExtractor.extractUserId(claims);
        if (userId == null) {
            log.warn("STOMP CONNECT rejected: userId claim absent in JWT");
            throw new MessageDeliveryException(message,
                    "STOMP CONNECT rejected: userId claim missing from JWT");
        }

        // Bind userId as the STOMP session principal.
        // Spring STOMP registers this in SimpUserRegistry automatically.
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                userId, null, Collections.emptyList()));

        log.debug("STOMP CONNECT accepted: userId={} bound as session principal", userId);
        return message;
    }

    /**
     * Extracts the raw JWT from the STOMP Authorization header (Bearer scheme).
     * Returns null if absent or not prefixed with "Bearer ".
     */
    private String extractBearerToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
