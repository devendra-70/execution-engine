package com.epam.execution_engine_service.gateway.security;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.gateway.exception.AuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * JWT Token Provider
 * Handles JWT token validation and claims extraction
 * Uses HMAC-SHA256 with configurable secret key
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    
    private final ApplicationProperties applicationProperties;
    
    /**
     * Validates JWT token and extracts claims
     * @param token The JWT token
     * @return Claims object if token is valid
     * @throws AuthenticationException if token is invalid
     */
    public Claims validateAndExtractClaims(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                    applicationProperties.getJwt().getSecretKey().getBytes()
            );
            
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new AuthenticationException("Invalid JWT token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if token is valid
     * @param token The JWT token
     * @return true if token is valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            validateAndExtractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
