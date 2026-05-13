package com.epam.execution_engine_service.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenProviderTest — Unit tests for JwtTokenProvider (SRS §3.1)
 * 
 * Coverage target: ≥100% (utilities)
 */
@ExtendWith(MockitoExtension.class)
public class JwtTokenProviderTest {

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private String secretKey;
    private String validToken;

    @BeforeEach
    public void setUp() {
        secretKey = "my-secret-key-that-is-long-enough-for-hmac-sha256";
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", secretKey);

        // Generate a valid token
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 123L);

        validToken = Jwts.builder()
                .claims(claims)
                .subject("testuser")
                .signWith(key)
                .compact();
    }

    @Test
    public void testValidateAndGetClaims_ValidToken() {
        // Act
        Optional<Claims> result = jwtTokenProvider.validateAndGetClaims(validToken);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getSubject());
    }

    @Test
    public void testValidateAndGetClaims_InvalidToken() {
        // Act
        Optional<Claims> result = jwtTokenProvider.validateAndGetClaims("invalid-token");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtractUserId_Success() {
        // Arrange
        Optional<Claims> claims = jwtTokenProvider.validateAndGetClaims(validToken);

        // Act
        Long userId = jwtTokenProvider.extractUserId(claims.get());

        // Assert
        assertEquals(123L, userId);
    }

    @Test
    public void testExtractSubject_Success() {
        // Arrange
        Optional<Claims> claims = jwtTokenProvider.validateAndGetClaims(validToken);

        // Act
        String subject = jwtTokenProvider.extractSubject(claims.get());

        // Assert
        assertEquals("testuser", subject);
    }

}
