package com.epam.execution_engine_service;

import com.epam.execution_engine_service.config.SecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test that verifies Spring Boot application context loads successfully.
 * Uses ActiveProfiles("test") to load test-specific configuration.
 * Imports SecurityTestConfig to provide a mock JwtDecoder for testing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SecurityTestConfig.class)
class ExecutionEngineServiceApplicationTests {

    @Test
    void contextLoads() {
        // Context should load without errors
    }

}
