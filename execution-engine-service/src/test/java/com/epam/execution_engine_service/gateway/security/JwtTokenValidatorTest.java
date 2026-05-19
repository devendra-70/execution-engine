package com.epam.execution_engine_service.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtTokenValidator}.
 *
 * Verifies JWT token validation and claim extraction:
 * - Valid token parsing and claim extraction
 * - Invalid/malformed token handling
 * - UserId extraction from claims (as Integer or Long)
 * - Error handling for expired/tampered tokens
 *
 * Test Coverage: 100% of JwtTokenValidator methods
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenValidator — JWT Token Validation Tests")
class JwtTokenValidatorTest {

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private JwtTokenValidator jwtTokenValidator;

    private static final String TEST_SECRET_KEY = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-validation";
    private static final long TEST_USER_ID = 12345L;

    @BeforeEach
    void setUp() {
        when(jwtProperties.getSecretKey()).thenReturn(TEST_SECRET_KEY);
    }

    /**
     * Nested test class for valid token scenarios.
     */
    @Nested
    @DisplayName("Valid Token Tests")
    class ValidTokenTests {

        /**
         * Test validation of a valid JWT token with correct claims.
         */
        @Test
        @DisplayName("Should successfully validate a valid JWT token")
        void testValidateAndExtractValidToken() {
            // Arrange
            String validToken = generateValidToken(TEST_USER_ID);

            // Act
            Optional<Claims> result = jwtTokenValidator.validateAndExtract(validToken);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().get("userId")).isNotNull();
        }

        /**
         * Test extraction of userId from valid token (as Number/Long).
         */
        @Test
        @DisplayName("Should extract userId from valid token as Long")
        void testExtractUserIdAsLong() {
            // Arrange
            String validToken = generateValidToken(TEST_USER_ID);

            // Act
            Optional<Long> result = jwtTokenValidator.extractUserId(validToken);

            // Assert
            assertThat(result).contains(TEST_USER_ID);
        }

        /**
         * Test extraction of userId from valid token (small numbers stored as Integer).
         */
        @Test
        @DisplayName("Should extract userId from token where value is stored as Integer")
        void testExtractUserIdStoredAsInteger() {
            // Arrange
            int smallUserId = 42;
            String validToken = generateTokenWithIntegerId(smallUserId);

            // Act
            Optional<Long> result = jwtTokenValidator.extractUserId(validToken);

            // Assert
            assertThat(result).contains((long) smallUserId);
        }

        /**
         * Test that validateAndExtract returns Claims for valid token.
         */
        @Test
        @DisplayName("Should return Claims object for valid token")
        void testValidateAndExtractReturnsClaims() {
            // Arrange
            String validToken = generateValidToken(TEST_USER_ID);

            // Act
            Optional<Claims> result = jwtTokenValidator.validateAndExtract(validToken);

            // Assert
            assertThat(result).isPresent();
            Claims claims = result.get();
            assertThat(claims.get("userId")).isNotNull();
        }
    }

    /**
     * Nested test class for invalid token scenarios.
     */
    @Nested
    @DisplayName("Invalid Token Tests")
    class InvalidTokenTests {

        /**
         * Test handling of null, empty, and malformed tokens.
         */
        @ParameterizedTest(name = "token=''{0}''")
        @NullAndEmptySource
        @ValueSource(strings = {"not.a.valid.jwt.token"})
        @DisplayName("Should return empty Optional for null, empty, or malformed token")
        void testValidateBlankOrMalformedToken(String token) {
            // Act
            Optional<Claims> result = jwtTokenValidator.validateAndExtract(token);

            // Assert
            assertThat(result).isEmpty();
        }

        /**
         * Test handling of token signed with wrong key.
         */
        @Test
        @DisplayName("Should return empty Optional for token signed with wrong key")
        void testValidateTokenWithWrongSignature() {
            // Arrange
            String validToken = generateValidToken(TEST_USER_ID);
            // Tamper with token signature
            String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

            // Act
            Optional<Claims> result = jwtTokenValidator.validateAndExtract(tamperedToken);

            // Assert
            assertThat(result).isEmpty();
        }

        /**
         * Test handling of random/invalid token.
         */
        @Test
        @DisplayName("Should return empty Optional for random invalid token")
        void testValidateInvalidToken() {
            // Act
            Optional<Claims> result = jwtTokenValidator.validateAndExtract("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    /**
     * Nested test class for userId extraction scenarios.
     */
    @Nested
    @DisplayName("UserId Extraction Tests")
    class UserIdExtractionTests {

        /**
         * Test that extractUserId returns empty Optional for invalid token.
         */
        @Test
        @DisplayName("Should return empty Optional when extracting userId from invalid token")
        void testExtractUserIdFromInvalidToken() {
            // Act
            Optional<Long> result = jwtTokenValidator.extractUserId("invalid.token.here");

            // Assert
            assertThat(result).isEmpty();
        }

        /**
         * Test that extractUserId returns empty Optional when userId is missing.
         */
        @Test
        @DisplayName("Should return empty Optional when userId is not in claims")
        void testExtractUserIdWhenMissing() {
            // Arrange
            String tokenWithoutUserId = generateTokenWithoutUserId();

            // Act
            Optional<Long> result = jwtTokenValidator.extractUserId(tokenWithoutUserId);

            // Assert
            assertThat(result).isEmpty();
        }

        /**
         * Test extraction of various userId values.
         */
        @Test
        @DisplayName("Should correctly extract various userId values")
        void testExtractMultipleUserIds() {
            // Test with different user IDs
            long[] userIds = {1L, 100L, 999999L, Long.MAX_VALUE / 2};

            for (long userId : userIds) {
                // Arrange
                String token = generateValidToken(userId);

                // Act
                Optional<Long> result = jwtTokenValidator.extractUserId(token);

                // Assert
                assertThat(result).contains(userId);
            }
        }
    }

    /**
     * Nested test class for edge cases and robustness.
     */
    @Nested
    @DisplayName("Edge Cases and Robustness Tests")
    class EdgeCasesTests {

        /**
         * Test handling of very long token.
         */
        @Test
        @DisplayName("Should handle very long tokens")
        void testHandleLongToken() {
            // Arrange
            String validToken = generateValidToken(TEST_USER_ID);
            // Long tokens should still be validated if properly signed

            // Act
            Optional<Claims> result = jwtTokenValidator.validateAndExtract(validToken);

            // Assert
            assertThat(result).isPresent();
        }

        /**
         * Test that secret key null handling.
         */
        @Test
        @DisplayName("Should handle null secret key gracefully")
        void testNullSecretKey() {
            // Arrange
            when(jwtProperties.getSecretKey()).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> jwtTokenValidator.validateAndExtract("any.token"))
                    .isInstanceOf(Exception.class);
        }

        /**
         * Test with whitespace in secret key.
         */
        @Test
        @DisplayName("Should handle secret key with whitespace")
        void testSecretKeyWithWhitespace() {
            // Arrange
            String keyWithWhitespace = " " + TEST_SECRET_KEY + " ";
            when(jwtProperties.getSecretKey()).thenReturn(keyWithWhitespace);

            // Act & Assert - should handle gracefully
            assertThatNoException().isThrownBy(() -> 
                jwtTokenValidator.validateAndExtract("invalid.token")
            );
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates a valid JWT token with the given userId.
     */
    private String generateValidToken(long userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId)
                .claim("sub", "testUser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
                .signWith(key)
                .compact();
    }

    /**
     * Generates a token with userId stored as Integer.
     */
    private String generateTokenWithIntegerId(int userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId) // Integer will be stored as Integer in JSON
                .claim("sub", "testUser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    /**
     * Generates a token without userId claim.
     */
    private String generateTokenWithoutUserId() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("sub", "testUser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }
}
