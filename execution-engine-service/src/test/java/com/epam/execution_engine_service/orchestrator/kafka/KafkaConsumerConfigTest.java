package com.epam.execution_engine_service.orchestrator.kafka;

import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaConsumerConfig — Unit Tests")
class KafkaConsumerConfigTest {

    @Mock
    private ThreadPoolTaskExecutor kafkaListenerPool;

    private KafkaConsumerConfig buildConfig(String bootstrapServers, int concurrency) {
        KafkaConsumerConfig cfg = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(cfg, "bootstrapServers", bootstrapServers);
        ReflectionTestUtils.setField(cfg, "concurrency", concurrency);
        return cfg;
    }

    // -----------------------------------------------------------------------
    // consumerFactory
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("consumerFactory bean")
    class ConsumerFactoryTests {

        @Test
        @DisplayName("returns a non-null DefaultKafkaConsumerFactory")
        void beanIsNotNull() {
            KafkaConsumerConfig cfg = buildConfig("localhost:9092", 5);
            ConsumerFactory<String, ExecutionTaskEvent> factory = cfg.consumerFactory();
            assertThat(factory).isNotNull().isInstanceOf(DefaultKafkaConsumerFactory.class);
        }

        @Test
        @DisplayName("bootstrap servers property is set correctly")
        void bootstrapServersProperty() {
            KafkaConsumerConfig cfg = buildConfig("broker1:9092,broker2:9092", 5);
            DefaultKafkaConsumerFactory<?, ?> factory =
                    (DefaultKafkaConsumerFactory<?, ?>) cfg.consumerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092,broker2:9092");
        }

        @Test
        @DisplayName("group-id is 'execution-engine-group'")
        void groupId() {
            KafkaConsumerConfig cfg = buildConfig("localhost:9092", 5);
            DefaultKafkaConsumerFactory<?, ?> factory =
                    (DefaultKafkaConsumerFactory<?, ?>) cfg.consumerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "execution-engine-group");
        }

        @Test
        @DisplayName("auto-commit is disabled")
        void autoCommitDisabled() {
            KafkaConsumerConfig cfg = buildConfig("localhost:9092", 5);
            DefaultKafkaConsumerFactory<?, ?> factory =
                    (DefaultKafkaConsumerFactory<?, ?>) cfg.consumerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        }

        @Test
        @DisplayName("auto-offset-reset is 'earliest'")
        void autoOffsetResetEarliest() {
            KafkaConsumerConfig cfg = buildConfig("localhost:9092", 5);
            DefaultKafkaConsumerFactory<?, ?> factory =
                    (DefaultKafkaConsumerFactory<?, ?>) cfg.consumerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        }

        @Test
        @DisplayName("key deserializer is StringDeserializer")
        void keyDeserializerClass() {
            KafkaConsumerConfig cfg = buildConfig("localhost:9092", 5);
            DefaultKafkaConsumerFactory<?, ?> factory =
                    (DefaultKafkaConsumerFactory<?, ?>) cfg.consumerFactory();
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        }
    }

    // -----------------------------------------------------------------------
    // kafkaListenerContainerFactory
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("kafkaListenerContainerFactory bean")
    class ContainerFactoryTests {

        @SuppressWarnings("unchecked")
        private ConcurrentKafkaListenerContainerFactory<String, ExecutionTaskEvent>
        buildContainerFactory(int concurrency) {
            KafkaConsumerConfig cfg = buildConfig("localhost:9092", concurrency);
            ConsumerFactory<String, ExecutionTaskEvent> consumerFactory = cfg.consumerFactory();
            return cfg.kafkaListenerContainerFactory(consumerFactory, kafkaListenerPool);
        }

        @Test
        @DisplayName("factory bean is non-null")
        void factoryNotNull() {
            assertThat(buildContainerFactory(3)).isNotNull();
        }

        @Test
        @DisplayName("concurrency is applied from property")
        void concurrencyIsSet() {
            ConcurrentKafkaListenerContainerFactory<String, ExecutionTaskEvent> factory =
                    buildContainerFactory(7);
            int concurrency = (int) ReflectionTestUtils.getField(factory, "concurrency");
            assertThat(concurrency).isEqualTo(7);
        }

        @Test
        @DisplayName("ack mode is MANUAL_IMMEDIATE")
        void ackModeManualImmediate() {
            ConcurrentKafkaListenerContainerFactory<String, ExecutionTaskEvent> factory =
                    buildContainerFactory(3);
            assertThat(factory.getContainerProperties().getAckMode())
                    .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        }

        @Test
        @DisplayName("listener task executor is the injected kafkaListenerPool")
        void listenerTaskExecutorIsSet() {
            ConcurrentKafkaListenerContainerFactory<String, ExecutionTaskEvent> factory =
                    buildContainerFactory(3);
            assertThat(factory.getContainerProperties().getListenerTaskExecutor())
                    .isSameAs(kafkaListenerPool);
        }
    }
}
