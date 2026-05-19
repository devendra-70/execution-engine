package com.epam.execution_engine_service.orchestrator.kafka;

import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaProducerConfig — Unit Tests")
class KafkaProducerConfigTest {

    private KafkaProducerConfig buildConfig(String bootstrapServers, String topic, int partitions) {
        KafkaProducerConfig cfg = new KafkaProducerConfig();
        ReflectionTestUtils.setField(cfg, "bootstrapServers", bootstrapServers);
        ReflectionTestUtils.setField(cfg, "topicName", topic);
        ReflectionTestUtils.setField(cfg, "partitions", partitions);
        return cfg;
    }

    // -----------------------------------------------------------------------
    // executionTasksTopic
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("executionTasksTopic bean")
    class ExecutionTasksTopicTests {

        @Test
        @DisplayName("returns a non-null NewTopic")
        void topicNotNull() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "my-topic", 10);
            assertThat(cfg.executionTasksTopic()).isNotNull();
        }

        @Test
        @DisplayName("topic name matches the configured value")
        void topicName() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "execution-tasks", 10);
            NewTopic topic = cfg.executionTasksTopic();
            assertThat(topic.name()).isEqualTo("execution-tasks");
        }

        @Test
        @DisplayName("partition count matches the configured value")
        void partitionCount() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "execution-tasks", 30);
            NewTopic topic = cfg.executionTasksTopic();
            assertThat(topic.numPartitions()).isEqualTo(30);
        }

        @Test
        @DisplayName("replication factor is 1")
        void replicationFactor() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "execution-tasks", 10);
            NewTopic topic = cfg.executionTasksTopic();
            assertThat(topic.replicationFactor()).isEqualTo((short) 1);
        }
    }

    // -----------------------------------------------------------------------
    // producerFactory
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("producerFactory bean")
    class ProducerFactoryTests {

        @Test
        @DisplayName("returns a non-null DefaultKafkaProducerFactory")
        void factoryNotNull() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "t", 1);
            ProducerFactory<String, ExecutionTaskEvent> factory = cfg.producerFactory();
            assertThat(factory).isNotNull().isInstanceOf(DefaultKafkaProducerFactory.class);
        }

        @Test
        @DisplayName("bootstrap servers property is set correctly")
        void bootstrapServers() {
            KafkaProducerConfig cfg = buildConfig("broker1:9092", "t", 1);
            DefaultKafkaProducerFactory<?, ?> factory =
                    (DefaultKafkaProducerFactory<?, ?>) cfg.producerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092");
        }

        @Test
        @DisplayName("key serializer is StringSerializer")
        void keySerializer() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "t", 1);
            DefaultKafkaProducerFactory<?, ?> factory =
                    (DefaultKafkaProducerFactory<?, ?>) cfg.producerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        }

        @Test
        @DisplayName("value serializer is JsonSerializer")
        void valueSerializer() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "t", 1);
            DefaultKafkaProducerFactory<?, ?> factory =
                    (DefaultKafkaProducerFactory<?, ?>) cfg.producerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        }

        @Test
        @DisplayName("acks config is 'all'")
        void acksAll() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "t", 1);
            DefaultKafkaProducerFactory<?, ?> factory =
                    (DefaultKafkaProducerFactory<?, ?>) cfg.producerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "all");
        }

        @Test
        @DisplayName("idempotence is enabled")
        void idempotenceEnabled() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "t", 1);
            DefaultKafkaProducerFactory<?, ?> factory =
                    (DefaultKafkaProducerFactory<?, ?>) cfg.producerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        }

        @Test
        @DisplayName("retries is set to 3")
        void retriesIsThree() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "t", 1);
            DefaultKafkaProducerFactory<?, ?> factory =
                    (DefaultKafkaProducerFactory<?, ?>) cfg.producerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ProducerConfig.RETRIES_CONFIG, 3);
        }
    }

    // -----------------------------------------------------------------------
    // kafkaTemplate
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("kafkaTemplate bean")
    class KafkaTemplateTests {

        @Test
        @DisplayName("returns a non-null KafkaTemplate")
        void templateNotNull() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "t", 1);
            KafkaTemplate<String, ExecutionTaskEvent> template = cfg.kafkaTemplate();
            assertThat(template).isNotNull();
        }

        @Test
        @DisplayName("each call produces a new KafkaTemplate instance")
        void eachCallProducesNewInstance() {
            KafkaProducerConfig cfg = buildConfig("localhost:9092", "t", 1);
            assertThat(cfg.kafkaTemplate()).isNotSameAs(cfg.kafkaTemplate());
        }
    }
}
