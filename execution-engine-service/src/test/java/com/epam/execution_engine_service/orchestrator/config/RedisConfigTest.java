package com.epam.execution_engine_service.orchestrator.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisConfig — Unit Tests")
class RedisConfigTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    private final RedisConfig redisConfig = new RedisConfig();

    // -----------------------------------------------------------------------
    // Bean creation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("redisMessageListenerContainer bean")
    class ContainerBeanTests {

        @Test
        @DisplayName("bean is non-null")
        void beanIsNotNull() {
            RedisMessageListenerContainer container =
                    redisConfig.redisMessageListenerContainer(connectionFactory);
            assertThat(container).isNotNull();
        }

        @Test
        @DisplayName("container has the supplied connection factory set")
        void connectionFactoryIsSet() {
            RedisMessageListenerContainer container =
                    redisConfig.redisMessageListenerContainer(connectionFactory);
            assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
        }

        @Test
        @DisplayName("each call produces a distinct container instance")
        void eachCallProducesNewInstance() {
            RedisMessageListenerContainer c1 =
                    redisConfig.redisMessageListenerContainer(connectionFactory);
            RedisMessageListenerContainer c2 =
                    redisConfig.redisMessageListenerContainer(connectionFactory);
            assertThat(c1).isNotSameAs(c2);
        }
    }
}
