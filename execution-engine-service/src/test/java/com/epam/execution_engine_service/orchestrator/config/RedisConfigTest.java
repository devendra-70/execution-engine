package com.epam.execution_engine_service.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    void testRedisMessageListenerContainerCreation() {
        RedisMessageListenerContainer container = redisConfig.redisMessageListenerContainer(redisConnectionFactory);

        assertNotNull(container, "RedisMessageListenerContainer should not be null");
    }

    @Test
    void testRedisMessageListenerContainerHasConnectionFactory() {
        RedisMessageListenerContainer container = redisConfig.redisMessageListenerContainer(redisConnectionFactory);

        assertNotNull(container.getConnectionFactory(), "ConnectionFactory should be set in container");
        assertEquals(redisConnectionFactory, container.getConnectionFactory(), "ConnectionFactory should match the provided one");
    }

    @Test
    void testRedisMessageListenerContainerBeanName() {
        RedisMessageListenerContainer container = redisConfig.redisMessageListenerContainer(redisConnectionFactory);

        assertNotNull(container, "Container bean should exist");
    }

    @Test
    void testMultipleContainerCreations() {
        RedisMessageListenerContainer container1 = redisConfig.redisMessageListenerContainer(redisConnectionFactory);
        RedisMessageListenerContainer container2 = redisConfig.redisMessageListenerContainer(redisConnectionFactory);

        assertNotNull(container1, "First container should not be null");
        assertNotNull(container2, "Second container should not be null");
    }

    @Test
    void testRedisConfigBeanInstantiation() {
        RedisConfig config = new RedisConfig();
        assertNotNull(config, "RedisConfig should be instantiable");
    }
}



