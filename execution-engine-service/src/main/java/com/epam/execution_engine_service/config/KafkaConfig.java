package com.epam.execution_engine_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka configuration
 * Configures KafkaTemplate for publishing ExecutionTaskEvent messages
 */
@Configuration
public class KafkaConfig {
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        // Default: blocking send (synchronous)
        template.setDefaultTopic("execution-tasks");
        return template;
    }
}
