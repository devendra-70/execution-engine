package com.epam.execution_engine_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.backoff.FixedBackOff;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Kafka configuration for the Execution Engine.
 *
 * <p>SRS §4.1 — Thread Pool Architecture:
 * Pool A (Kafka Listener Pool) consumes from execution-tasks with manual offset acknowledgement.
 * Offset is committed by Pool A ONLY after Pool B (ExecutionOrchestrator) successfully
 * persists the result to PostgreSQL.
 *
 * <p>SRS §10 — Error Handling:
 * DB failure causes transaction rollback. Kafka offset NOT committed.
 * Task gracefully retried via seek-back error handler.
 *
 * <p>EPMICMPCOD-513: Manual offset ack mode configuration.
 * EPMICMPCOD-515: Seek-back error handler for DB failure recovery.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final ApplicationProperties appProperties;

    /**
     * Pool B — TaskExecutor for container acquisition and orchestration (SRS §4.1).
     *
     * <p>Thread count sourced from {@code app.execution.orchestration-threads} (SRS §12).
     * Pool A listener threads submit work here and block on the result (to honour the
     * ack-after-commit guarantee of SRS §5.2).  Pool B threads perform the heavy
     * container I/O, keeping Kafka listener threads separate from I/O-bound work.
     *
     * <p>EPMICMPCOD-353: Realises the two-pool architecture mandated by SRS §4.1.
     */
    @Bean(name = "executionTaskExecutor")
    public Executor executionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = appProperties.getExecution().getOrchestrationThreads();
        executor.setCorePoolSize(threads > 0 ? threads : 50);
        executor.setMaxPoolSize(threads > 0 ? threads : 50);
        // Queue capacity provides burst buffering; CallerRunsPolicy applies backpressure on Pool A
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("pool-b-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * KafkaTemplate for publishing ExecutionTaskEvent messages to execution-tasks topic.
     * Producer acks=all (idempotent) per SRS §7.1.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        template.setDefaultTopic("execution-tasks");
        return template;
    }

    /**
     * Kafka Listener Container Factory for Pool A (SRS §4.1).
     *
     * <ul>
     *   <li>AckMode: {@code MANUAL_IMMEDIATE} — offset committed only on explicit
     *       {@code acknowledgment.acknowledge()} call from Pool A listener.</li>
     *   <li>enable.auto.commit=false — enforced via application.properties.</li>
     *   <li>Concurrency: sourced from {@code app.kafka.concurrency} (SRS §12).</li>
     *   <li>Error handler: {@link #seekToCurrentErrorHandler()} — seeks back on exception
     *       so failed message is redelivered on the next poll (SRS §10).</li>
     * </ul>
     *
     * <p>EPMICMPCOD-513: Manual AckMode configuration.
     * EPMICMPCOD-515: Error handler wired here.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Pool A concurrency — configurable via app.kafka.concurrency (SRS §12)
        int concurrency = appProperties.getKafka().getConcurrency();
        factory.setConcurrency(concurrency > 0 ? concurrency : 1);

        // Manual offset acknowledgement — Pool A calls ack.acknowledge() explicitly (SRS §4.1, §5.2)
        // Pool B (ExecutionOrchestrator) NEVER touches the Acknowledgment handle (SRS §4.1 Business Rule #3)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Seek-back error handler — on exception, consumer rewinds to failed offset (SRS §10)
        factory.setCommonErrorHandler(seekToCurrentErrorHandler(kafkaTemplate));

        return factory;
    }

    /**
     * Seek-back error handler with Dead Letter Topic recovery (SRS §10, H1 — EPMICMPCOD-353).
     *
     * <p>After {@code FixedBackOff(1 000 ms, 3 retries)} are exhausted, the record is published
     * to the DLT ({@code execution-tasks.DLT}) via {@link DeadLetterPublishingRecoverer} so no
     * {@code ExecutionTaskEvent} is silently dropped.  Partition alignment is preserved.
     *
     * <p>Bean dependency: {@code kafkaTemplate} → {@code ProducerFactory} (no circular dependency).
     *
     * @param kafkaTemplate the producer template used to publish exhausted records to the DLT
     * @return configured {@link DefaultErrorHandler}
     */
    @Bean
    public DefaultErrorHandler seekToCurrentErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate) {

        // After max retries, publish to DLT — preserves partition alignment and audit trail (SRS §10)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (r, e) -> new TopicPartition(
                        appProperties.getKafka().getTopic() + ".DLT",
                        r.partition()));

        // 3 retries × 1 000 ms back-off before routing to DLT
        FixedBackOff backOff = new FixedBackOff(1_000L, 3L);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Seek consumer back to the failed offset so message is redelivered on retry
        handler.setSeekAfterError(true);
        return handler;
    }
}
