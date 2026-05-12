package com.epam.execution_engine_service.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for test profile.
 * This configuration activates when @ActiveProfiles("test") is used in test classes.
 */
@Profile("test")
@TestConfiguration
public class SecurityTestConfig {
    // No beans needed - tests use @WithMockUser for authentication
    // JWT validation is handled by JwtAuthenticationFilter with custom JwtTokenProvider
}
