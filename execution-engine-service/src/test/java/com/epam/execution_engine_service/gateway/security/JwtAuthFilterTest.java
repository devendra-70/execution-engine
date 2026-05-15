package com.epam.execution_engine_service.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.epam.execution_engine_service.gateway.JwtAuthFilter;
import com.epam.execution_engine_service.gateway.JwtTokenValidator;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthFilter}.
 *
 * Verifies JWT authentication filter behavior:
 * - Token extraction from Authorization header (Bearer scheme)
 * - Token extraction from query parameter (for WebSocket)
 * - Valid token processing and SecurityContext setup
 * - Invalid token handling (no SecurityContext set)
 * - Filter chain continuation in all scenarios
 * - Principal extraction (userId or subject)
 *
 * Test Coverage: 100% of JwtAuthFilter methods and scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter — JWT Authentication Filter Tests")
class JwtAuthFilterTest {

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    private static final String TEST_SECRET_KEY = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-validation";
    private static final long TEST_USER_ID = 12345L;
    private static final String TEST_SUBJECT = "testUser";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Nested test class for valid token scenarios.
     */
    @Nested
    @DisplayName("Valid Token Tests")
    class ValidTokenTests {

        /**
         * Test successful authentication with valid token in Authorization header.
         */
        @Test
        @DisplayName("Should set SecurityContext for valid Bearer token in Authorization header")
        void testValidBearerTokenInHeader() throws ServletException, IOException {
            // Arrange
            String validToken = "valid-jwt-token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(request.getParameter("token")).thenReturn(null);
            
            Claims claims = createMockClaims(TEST_USER_ID);
            when(jwtTokenValidator.validateAndExtract(validToken)).thenReturn(Optional.of(claims));

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getPrincipal()).isEqualTo(String.valueOf(TEST_USER_ID));
            assertThat(authentication.getAuthorities()).isNotEmpty();
            verify(filterChain).doFilter(request, response);
        }

        /**
         * Test successful authentication with valid token in query parameter.
         */
        @Test
        @DisplayName("Should set SecurityContext for valid token in query parameter")
        void testValidTokenInQueryParameter() throws ServletException, IOException {
            // Arrange
            String validToken = "valid-jwt-token-from-query";
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getParameter("token")).thenReturn(validToken);
            
            Claims claims = createMockClaims(TEST_USER_ID);
            when(jwtTokenValidator.validateAndExtract(validToken)).thenReturn(Optional.of(claims));

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getPrincipal()).isEqualTo(String.valueOf(TEST_USER_ID));
            verify(filterChain).doFilter(request, response);
        }

        /**
         * Test authentication with userId as Long value in claims.
         */
        @Test
        @DisplayName("Should correctly handle userId as Long in claims")
        void testUserIdAsLongInClaims() throws ServletException, IOException {
            // Arrange
            String validToken = "valid-jwt-token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(request.getParameter("token")).thenReturn(null);
            
            Claims claims = createMockClaims(TEST_USER_ID);
            when(jwtTokenValidator.validateAndExtract(validToken)).thenReturn(Optional.of(claims));

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.getPrincipal()).isEqualTo(String.valueOf(TEST_USER_ID));
        }

        /**
         * Test authentication with userId as Integer value in claims.
         */
        @Test
        @DisplayName("Should correctly handle userId as Integer in claims")
        void testUserIdAsIntegerInClaims() throws ServletException, IOException {
            // Arrange
            String validToken = "valid-jwt-token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(request.getParameter("token")).thenReturn(null);
            
            int smallUserId = 42;
            Claims claims = createMockClaimsWithIntegerId(smallUserId);
            when(jwtTokenValidator.validateAndExtract(validToken)).thenReturn(Optional.of(claims));

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.getPrincipal()).isEqualTo(String.valueOf(smallUserId));
        }

        /**
         * Test that authorities are set correctly.
         */
        @Test
        @DisplayName("Should set ROLE_USER authority for authenticated user")
        void testAuthoritySetCorrectly() throws ServletException, IOException {
            // Arrange
            String validToken = "valid-jwt-token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(request.getParameter("token")).thenReturn(null);
            
            Claims claims = createMockClaims(TEST_USER_ID);
            when(jwtTokenValidator.validateAndExtract(validToken)).thenReturn(Optional.of(claims));

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.getAuthorities())
                    .extracting("authority")
                    .contains("ROLE_USER");
        }

        /**
         * Test fallback to subject when userId is not available.
         */
        @Test
        @DisplayName("Should use subject as principal when userId is not in claims")
        void testFallbackToSubject() throws ServletException, IOException {
            // Arrange
            String validToken = "valid-jwt-token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(request.getParameter("token")).thenReturn(null);
            
            Claims claims = createMockClaimsWithoutUserId(TEST_SUBJECT);
            when(jwtTokenValidator.validateAndExtract(validToken)).thenReturn(Optional.of(claims));

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.getPrincipal()).isEqualTo(TEST_SUBJECT);
        }
    }

    /**
     * Nested test class for invalid/missing token scenarios.
     */
    @Nested
    @DisplayName("Invalid Token Tests")
    class InvalidTokenTests {

        /**
         * Test that no token leads to unauthenticated request.
         */
        @Test
        @DisplayName("Should not set SecurityContext when no token is provided")
        void testNoTokenProvided() throws ServletException, IOException {
            // Arrange
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getParameter("token")).thenReturn(null);

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }

        /**
         * Test that invalid Bearer token is handled.
         */
        @Test
        @DisplayName("Should not set SecurityContext for invalid Bearer token")
        void testInvalidBearerToken() throws ServletException, IOException {
            // Arrange
            String invalidToken = "invalid-jwt-token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);
            when(request.getParameter("token")).thenReturn(null);
            
            when(jwtTokenValidator.validateAndExtract(invalidToken)).thenReturn(Optional.empty());

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }

        /**
         * Test that Authorization header without Bearer prefix is ignored.
         */
        @Test
        @DisplayName("Should ignore Authorization header without Bearer prefix")
        void testAuthHeaderWithoutBearerPrefix() throws ServletException, IOException {
            // Arrange
            when(request.getHeader("Authorization")).thenReturn("Basic dGVzdDp0ZXN0"); // Basic auth
            when(request.getParameter("token")).thenReturn(null);

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }

        /**
         * Test that empty Authorization header is handled.
         */
        @Test
        @DisplayName("Should handle empty Authorization header")
        void testEmptyAuthorizationHeader() throws ServletException, IOException {
            // Arrange
            when(request.getHeader("Authorization")).thenReturn("");
            when(request.getParameter("token")).thenReturn(null);

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }

        /**
         * Test that empty query parameter token is handled.
         */
        @Test
        @DisplayName("Should handle empty token query parameter")
        void testEmptyTokenParameter() throws ServletException, IOException {
            // Arrange
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getParameter("token")).thenReturn("");

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }

        /**
         * Test that claims without userId and without subject are handled.
         */
        @Test
        @DisplayName("Should not set SecurityContext when both userId and subject are missing")
        void testClaimsWithoutUserIdAndSubject() throws ServletException, IOException {
            // Arrange
            String validToken = "valid-jwt-token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(request.getParameter("token")).thenReturn(null);
            
            Claims claims = createMockClaimsWithNullPrincipal();
            when(jwtTokenValidator.validateAndExtract(validToken)).thenReturn(Optional.of(claims));

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    /**
     * Nested test class for filter chain continuation.
     */
    @Nested
    @DisplayName("Filter Chain Tests")
    class FilterChainTests {

        /**
         * Test that filter chain continues regardless of token validity.
         */
        @Test
        @DisplayName("Should always continue filter chain regardless of token validity")
        void testFilterChainContinuesForInvalidToken() throws ServletException, IOException {
            // Arrange
            when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
            when(request.getParameter("token")).thenReturn(null);
            when(jwtTokenValidator.validateAndExtract("invalid-token")).thenReturn(Optional.empty());

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
        }

        /**
         * Test that filter chain continues when no token is provided.
         */
        @Test
        @DisplayName("Should continue filter chain when no token is provided")
        void testFilterChainContinuesWhenNoToken() throws ServletException, IOException {
            // Arrange
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getParameter("token")).thenReturn(null);

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
        }

        /**
         * Test that filter chain continues with valid token.
         */
        @Test
        @DisplayName("Should continue filter chain with valid token")
        void testFilterChainContinuesWithValidToken() throws ServletException, IOException {
            // Arrange
            String validToken = "valid-jwt-token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
            when(request.getParameter("token")).thenReturn(null);
            
            Claims claims = createMockClaims(TEST_USER_ID);
            when(jwtTokenValidator.validateAndExtract(validToken)).thenReturn(Optional.of(claims));

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
        }
    }

    /**
     * Nested test class for token extraction priority.
     */
    @Nested
    @DisplayName("Token Extraction Priority Tests")
    class TokenExtractionPriorityTests {

        /**
         * Test that Authorization header has priority over query parameter.
         */
        @Test
        @DisplayName("Should prefer Authorization header over query parameter")
        void testAuthHeaderPriorityOverQueryParam() throws ServletException, IOException {
            // Arrange
            String headerToken = "header-token";
            String queryToken = "query-token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + headerToken);
            when(request.getParameter("token")).thenReturn(queryToken);
            
            Claims claims = createMockClaims(TEST_USER_ID);
            when(jwtTokenValidator.validateAndExtract(headerToken)).thenReturn(Optional.of(claims));

            // Act
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Assert
            // Verify that header token was used (validator called with header token)
            verify(jwtTokenValidator).validateAndExtract(headerToken);
            verify(jwtTokenValidator, never()).validateAndExtract(queryToken);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates a mock Claims object with userId.
     */
    private Claims createMockClaims(long userId) {
        Claims claims = mock(Claims.class);
        when(claims.get("userId")).thenReturn(userId);
        when(claims.getSubject()).thenReturn(TEST_SUBJECT);
        return claims;
    }

    /**
     * Creates a mock Claims object with userId as Integer.
     */
    private Claims createMockClaimsWithIntegerId(int userId) {
        Claims claims = mock(Claims.class);
        when(claims.get("userId")).thenReturn(userId); // Integer value
        when(claims.getSubject()).thenReturn(TEST_SUBJECT);
        return claims;
    }

    /**
     * Creates a mock Claims object without userId.
     */
    private Claims createMockClaimsWithoutUserId(String subject) {
        Claims claims = mock(Claims.class);
        when(claims.get("userId")).thenReturn(null);
        when(claims.getSubject()).thenReturn(subject);
        return claims;
    }

    /**
     * Creates a mock Claims object with null principal (no userId, no subject).
     */
    private Claims createMockClaimsWithNullPrincipal() {
        Claims claims = mock(Claims.class);
        when(claims.get("userId")).thenReturn(null);
        when(claims.getSubject()).thenReturn(null);
        return claims;
    }
}
