package com.epam.execution_engine_service.gateway.security;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * SRP/DRY: Single source of truth for the HMAC signing key.
 * Key is created once at startup and reused by all consumers:
 * {@link JwtTokenValidator}, {@link DevTokenController}, etc.
 * Eliminates per-request key construction overhead.
 */
@Component
@RequiredArgsConstructor
public class JwtKeyProvider {

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        secretKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    public SecretKey getKey() {
        return secretKey;
    }
}

