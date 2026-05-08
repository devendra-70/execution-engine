package com.epam.execution_engine_service.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.mockito.Mockito.mock;

/**
 * Test configuration providing a fully mocked Redis connection factory.
 * Prevents any actual Redis/Lettuce connection attempts during tests.
 * All Redis repository operations are handled via @MockBean in test classes.
 * (SRS Section 8: Redis key design and status storage)
 */
@Profile("test")
@TestConfiguration
public class TestRedisConfiguration {

    /**
     * Returns a Mockito mock for RedisConnectionFactory so that no real
     * Redis server is contacted during the test Spring context startup.
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }
}
