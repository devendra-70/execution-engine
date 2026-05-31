package com.epam.execution_engine_service.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SRP: Validates and parses JWTs.
 * DIP: Depends on {@link JwtKeyProvider} for the signing key — key construction is not repeated here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenValidator {

    private final JwtKeyProvider jwtKeyProvider;

    public Optional<Claims> validateAndExtract(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtKeyProvider.getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Safely extract userId regardless of whether JJWT stored the number as Integer or Long.
     */
    public Optional<Long> extractUserId(String token) {
        return validateAndExtract(token).map(claims -> {
            Object raw = claims.get("userId");
            if (raw instanceof Number) return ((Number) raw).longValue();
            return null;
        });
    }
}
