package com.epam.execution_engine_service.persistence.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * KafkaConfig — Kafka listener configuration (SRS §7, §4.1)
 * 
 * Configures:
 * - Kafka listener container with manual offset commit
 * - Concurrency level (Pool A thread count)
 * - JSON deserialization for ExecutionTaskEvent
 * - Error handling
 * 
 * Manual offset commit ensures offset is advanced ONLY after persistence succeeds.
 * 
 * Disabled in test environment via app.kafka.enabled=false in application-test.properties
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class KafkaConfig {

    @Value("${app.kafka.concurrency:25}")
    private int kafkaConcurrency;

    /**
     * Create Kafka listener container factory with manual offset commit
     * 
     * @param consumerFactory Spring's Kafka ConsumerFactory
     * @return ConcurrentKafkaListenerContainerFactory configured for manual acks
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(kafkaConcurrency);
        
        // Manual offset commit (SRS §5.2)
        // Offset advanced ONLY after ack.acknowledge() called in listener
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Error handling
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());
        
        return factory;
    }

    /**
     * Create KafkaTemplate for publishing ExecutionTaskEvent messages
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        // Default: blocking send (synchronous)
        template.setDefaultTopic("execution-tasks");
        return template;
    }

}
