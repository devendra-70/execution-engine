package com.epam.execution_engine_service.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Test configuration that provides a mock JWT decoder for testing purposes.
 * This avoids network calls to the actual JWT issuer during tests.
 *
 * Activated by @ActiveProfiles("test") in test classes.
 */
@Profile("test")
@TestConfiguration
public class SecurityTestConfig {

    /**
     * Creates a mock JWT decoder for testing that accepts any JWT-like string.
     * This allows tests to focus on controller and validation logic rather than OAuth2 token validation.
     *
     * @return JwtDecoder that raises an exception (Spring Security tests use @WithMockUser instead)
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        // For testing, we use a mock decoder that rejects all tokens
        // Tests should use @WithMockUser or @WithMockAuthentication for authentication
        return token -> {
            throw new JwtException("JWT decoder disabled for testing - use @WithMockUser instead");
        };
    }
}
