package com.epam.execution_engine_service.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * JwtTokenProvider — JWT token validation utility (SRS §3.1)
 * 
 * Validates JWT tokens using a shared secret key.
 * Extracts claims (userId, IP, etc.) for authentication context.
 * 
 * Simple token validation without full OAuth2 authorization server round-trips.
 * 
 * NOTE: Registered as @Component to ensure single bean instance in Spring context.
 * Multiple instances of this bean will cause conflicts - ensure only ONE @Component annotation exists.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret-key:dev-secret-key-do-not-use-in-production}")
    private String secretKey;

    /**
     * Validate JWT token and extract claims
     * @param token Bearer token (without "Bearer " prefix)
     * @return Optional containing Claims if valid, empty if invalid
     */
    public Optional<Claims> validateAndGetClaims(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return Optional.of(claims);
        } catch (JwtException e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Extract userId from claims
     * @param claims JWT claims
     * @return userId or null if not present
     */
    public Long extractUserId(Claims claims) {
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }
        if (userIdObj instanceof String) {
            try {
                return Long.parseLong((String) userIdObj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extract subject (typically username) from claims
     * @param claims JWT claims
     * @return subject or null if not present
     */
    public String extractSubject(Claims claims) {
        return claims.getSubject();
    }

}
