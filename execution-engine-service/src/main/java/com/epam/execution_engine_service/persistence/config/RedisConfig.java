package com.epam.execution_engine_service.persistence.config;

import com.epam.execution_engine_service.gateway.websocket.ExecutionResultMessageListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
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

    /**
     * Redis Pub/Sub channel topic for execution-completed broadcasts (SRS §8).
     * All ECS instances subscribe to this channel on startup.
     */
    @Bean
    public ChannelTopic executionCompletedTopic() {
        return new ChannelTopic("execution-completed");
    }

    /**
     * MessageListenerAdapter wrapping ExecutionResultMessageListener.
     * Delegates Redis Pub/Sub messages to the listener's onMessage() method.
     *
     * @param listener the ExecutionResultMessageListener bean
     * @return configured MessageListenerAdapter
     */
    @Bean
    public MessageListenerAdapter executionResultListenerAdapter(
            final ExecutionResultMessageListener listener) {
        return new MessageListenerAdapter(listener, "onMessage");
    }

    /**
     * RedisMessageListenerContainer — subscribes all ECS instances to the
     * execution-completed Pub/Sub channel on application startup (SRS §8).
     *
     * Subscription is established before the application serves HTTP traffic.
     * Not active in the "test" profile (TestRedisConfiguration uses a mock
     * RedisConnectionFactory that cannot support a real Pub/Sub subscription).
     *
     * @param connectionFactory Redis connection factory (shared, per SRS §3.3)
     * @param adapter           the delegating MessageListenerAdapter
     * @param topic             the execution-completed ChannelTopic
     * @return configured RedisMessageListenerContainer
     */
    @Bean
    @Profile("!test")
    public RedisMessageListenerContainer redisMessageListenerContainer(
            final RedisConnectionFactory connectionFactory,
            final MessageListenerAdapter adapter,
            final ChannelTopic topic) {
        final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(adapter, topic);
        return container;
    }
}
