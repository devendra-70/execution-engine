package com.epam.execution_engine_service.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import com.epam.execution_engine_service.dto.ExecutionTaskEvent;

import static org.mockito.Mockito.mock;

/**
 * Test configuration providing mocked external dependencies.
 * Prevents actual Redis/Kafka connections during test context initialization.
 * Excludes KafkaAutoConfiguration to prevent listener container startup.
 * 
 * (SRS Section 3: Simple JWT validation via custom JwtAuthenticationFilter)
 * (SRS Section 7: Kafka event publishing is mocked in tests)
 * (SRS Section 8: Redis key design and status storage is mocked in tests)
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
    OAuth2ResourceServerAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
})
public class TestRedisConfiguration {

    /**
     * Provide mocked RedisConnectionFactory for all test contexts.
     * Must be @Primary so it takes precedence over any auto-configured instance.
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    /**
     * Provide mocked RedisTemplate<String, Object> for test contexts.
     * Services like RateLimitingService depend on this bean.
     * Must be @Primary to override auto-configuration.
     */
    @Bean(name = "redisTemplate")
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        // Ensure the mock is properly configured
        return template;
    }

    @Bean
    @Primary
    public ProducerFactory<String, Object> producerFactory() {
        return mock(ProducerFactory.class);
    }

    @Bean
    @Primary
    public ConsumerFactory<String, Object> consumerFactory() {
        return mock(ConsumerFactory.class);
    }

    @Bean
    @Primary
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = mock(ConcurrentKafkaListenerContainerFactory.class);
        return factory;
    }

    @Bean
    @Primary
    public KafkaTemplate<String, ExecutionTaskEvent> kafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
