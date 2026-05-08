package com.epam.execution_engine_service;

import com.epam.execution_engine_service.config.SecurityTestConfig;
import com.epam.execution_engine_service.config.TestKafkaProducerConfiguration;
import com.epam.execution_engine_service.config.TestRedisConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test that verifies Spring Boot application context loads successfully.
 * Uses ActiveProfiles("test") to load test-specific configuration.
 * Imports SecurityTestConfig to provide a mock JwtDecoder for testing.
 * Imports TestRedisConfiguration and TestKafkaProducerConfiguration to prevent
 * real Redis and Kafka connections during context startup.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({SecurityTestConfig.class, TestRedisConfiguration.class, TestKafkaProducerConfiguration.class})
class ExecutionEngineServiceApplicationTests {

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void contextLoads() {
        // Context should load without errors
    }

}
