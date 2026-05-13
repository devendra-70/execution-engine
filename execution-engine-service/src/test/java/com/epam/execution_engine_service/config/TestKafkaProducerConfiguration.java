package com.epam.execution_engine_service.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;

import static org.mockito.Mockito.mock;

/**
 * Test configuration providing a mock Kafka ProducerFactory.
 * Prevents the auto-configured ProducerFactory from attempting broker connections.
 * KafkaTemplate itself is replaced via @MockBean in each test class.
 * (SRS Section 7.1: Kafka event publishing)
 */
@Profile("test")
@TestConfiguration
public class TestKafkaProducerConfiguration {

    /**
     * Replaces the auto-configured ProducerFactory with a mock so that
     * KafkaConfig.kafkaTemplate() receives a mock factory and never opens
     * a real connection to a Kafka broker.
     */
    @Bean
    @Primary
    public ProducerFactory<String, Object> producerFactory() {
        return mock(ProducerFactory.class);
    }

    /**
     * Mock ConsumerFactory required by KafkaConfig.kafkaListenerContainerFactory().
     * KafkaAutoConfiguration is excluded in the test profile, so no ConsumerFactory
     * is auto-configured. This mock satisfies the dependency without opening
     * a real broker connection. (EPMICMPCOD-513)
     */
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public ConsumerFactory<String, Object> consumerFactory() {
        return mock(ConsumerFactory.class);
    }
}
