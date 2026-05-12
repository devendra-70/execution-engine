package com.epam.execution_engine_service.gateway.security;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.gateway.exception.AuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;

/**
 * JWT Token Provider (DEPRECATED - use util.JwtTokenProvider instead)
 * 
 * This class is kept for backward compatibility but is no longer registered as a Spring component.
 * All JWT token validation should use com.epam.execution_engine_service.util.JwtTokenProvider.
 * 
 * Handles JWT token validation and claims extraction
 * Uses HMAC-SHA256 with configurable secret key
 */
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
