package com.epam.execution_engine_service.config;

import com.epam.execution_engine_service.service.ExecutionResultBroadcaster;
import com.epam.execution_engine_service.service.RedisExecutionStatusService;
import com.epam.execution_engine_service.service.SandboxClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.Mockito.mock;

/**
 * Test Configuration — Mock beans for orchestrator services (SRS §6, §8, §10)
 * 
 * Provides mock implementations of orchestrator pipeline services to enable
 * test context initialization without requiring real Redis/Socket connections.
 */
@TestConfiguration
public class TestServiceConfiguration {

    @Bean
    @Primary
    public RedisExecutionStatusService redisExecutionStatusService() {
        // Create a fully mocked instance (no constructor params needed)
        return mock(RedisExecutionStatusService.class);
    }

    @Bean
    @Primary
    public ExecutionResultBroadcaster executionResultBroadcaster() {
        return mock(ExecutionResultBroadcaster.class);
    }

    @Bean
    @Primary
    public SandboxClient sandboxClient() {
        return mock(SandboxClient.class);
    }

    /**
     * Mock for util.JwtTokenProvider required by JwtAuthenticationFilter → SecurityConfig
     * and by JwtStompInterceptor → WebSocketConfig.
     */
    @Bean
    @Primary
    public com.epam.execution_engine_service.util.JwtTokenProvider utilJwtTokenProvider() {
        return mock(com.epam.execution_engine_service.util.JwtTokenProvider.class);
    }

}

