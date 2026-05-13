package com.epam.execution_engine_service.persistence.config;

import com.epam.execution_engine_service.gateway.websocket.ExecutionResultMessageListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Redis connectivity and serialization.
 * Implements SRS §8 Redis design patterns.
 * Note: @EnableRedisRepositories is declared on ExecutionEngineServiceApplication
 * to resolve multi-module conflict with JPA.
 *
 * EPMICMPCOD-342: adds RedisMessageListenerContainer subscribed to
 * the execution-completed Pub/Sub channel (SRS §8).
 */
@Configuration("persistenceRedisConfig")
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
    /**
     * Primary RedisTemplate for String KV operations — used by RedisExecutionStatusService
     * and ExecutionResultPublishingService (SRS §8 — execution:status keys).
     */
    @Bean
    @Primary
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

    /**
     * RedisTemplate for Object/ZSet operations — used by RateLimitingService
     * which stores Long timestamps as ZSet members for the rolling-window
     * rate limiter (SRS §3.3 — ratelimit:user:{id} key pattern).
     *
     * @param connectionFactory Redis connection factory
     * @return configured RedisTemplate with Object value serializer
     */
    @Bean(name = "objectRedisTemplate")
    public RedisTemplate<String, Object> objectRedisTemplate(
        final RedisConnectionFactory connectionFactory
    ) {
        final RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        final StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // GenericToStringSerializer handles Long/Double ZSet members as UTF-8 strings
        template.setValueSerializer(new GenericToStringSerializer<>(Object.class));
        template.setHashValueSerializer(new GenericToStringSerializer<>(Object.class));
        template.afterPropertiesSet();

        return template;
    }

    /**
     * Redis Pub/Sub channel topic for execution-completed broadcasts (SRS §8).
     * All ECS instances subscribe to this channel on startup.
     */
    @Bean
    public ChannelTopic executionCompletedTopic() {
        return new ChannelTopic("execution-completed");
    }

    /**
     * RedisMessageListenerContainer — subscribes all ECS instances to the
     * execution-completed Pub/Sub channel on application startup (SRS §8).
     *
     * Subscription is established before the application serves HTTP traffic.
     * Not active in the "test" profile (TestRedisConfiguration uses a mock
     * RedisConnectionFactory that cannot support a real Pub/Sub subscription).
     *
     * @param connectionFactory              Redis connection factory (shared, per SRS §3.3)
     * @param executionResultMessageListener the listener that directly implements MessageListener
     * @param topic                          the execution-completed ChannelTopic
     * @return configured RedisMessageListenerContainer
     */
    @Bean
    @Profile("!test")
    public RedisMessageListenerContainer redisMessageListenerContainer(
            final RedisConnectionFactory connectionFactory,
            final ExecutionResultMessageListener executionResultMessageListener,
            final ChannelTopic topic) {
        final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(executionResultMessageListener, topic);
        return container;
    }
}
