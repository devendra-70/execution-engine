package com.epam.execution_engine_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Redis connectivity and serialization.
 * Implements SRS §8 Redis design patterns:
 * - KV pattern: execution:status:{executionId}
 * - Pub/Sub pattern: execution-completed channel
 *
 * Connection factory and host/port are configured via:
 * - spring.redis.host
 * - spring.redis.port
 * (externalized in application.properties for environment-specific config)
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate bean for Redis operations (KV and Pub/Sub).
     * Configured for String keys and String values (JSON payloads).
     *
     * Serialization:
     * - Keys: UTF-8 StringRedisSerializer
     * - Values: UTF-8 StringRedisSerializer (for JSON)
     * - Hash keys/values: UTF-8 StringRedisSerializer
     *
     * @param connectionFactory Redis connection factory
     * @return configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(
        final RedisConnectionFactory connectionFactory
    ) {
        final RedisTemplate<String, String> template = new RedisTemplate<>();

        // Set connection factory
        template.setConnectionFactory(connectionFactory);

        // Configure serializers for keys and values
        final StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // String keys
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // String values (for JSON payloads)
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        // Initialize
        template.afterPropertiesSet();

        return template;
    }
}
