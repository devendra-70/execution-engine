package com.epam.execution_engine_service.orchestrator.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@Import(ThreadPoolConfig.class)
@TestPropertySource(properties = {
        "app.kafka.concurrency=4",
        "app.execution.orchestration-threads=6"
})
@DisplayName("ThreadPoolConfig — Unit Tests")
class ThreadPoolConfigTest {

    @Autowired
    private ThreadPoolTaskExecutor kafkaListenerPool;

    @Autowired
    private ThreadPoolTaskExecutor orchestrationPool;

    @AfterEach
    void cleanup() {
        kafkaListenerPool.getThreadPoolExecutor().purge();
        orchestrationPool.getThreadPoolExecutor().purge();
    }

    // -----------------------------------------------------------------------
    // kafkaListenerPool
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("kafkaListenerPool bean")
    class KafkaListenerPoolTests {

        @Test
        @DisplayName("bean is non-null and is a ThreadPoolTaskExecutor")
        void beanCreated() {
            assertThat(kafkaListenerPool).isNotNull();
        }

        @Test
        @DisplayName("core pool size equals app.kafka.concurrency (4)")
        void corePoolSize() {
            assertThat(kafkaListenerPool.getCorePoolSize()).isEqualTo(4);
        }

        @Test
        @DisplayName("max pool size is double the core pool size (8)")
        void maxPoolSize() {
            assertThat(kafkaListenerPool.getMaxPoolSize()).isEqualTo(8);
        }

        @Test
        @DisplayName("queue capacity is 100")
        void queueCapacity() {
            assertThat(kafkaListenerPool.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(100);
        }

        @Test
        @DisplayName("thread name prefix is 'kafka-listener-'")
        void threadNamePrefix() {
            assertThat(kafkaListenerPool.getThreadNamePrefix()).isEqualTo("kafka-listener-");
        }

        @Test
        @DisplayName("submitted tasks actually execute")
        void tasksExecute() throws Exception {
            AtomicBoolean ran = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            kafkaListenerPool.execute(() -> {
                ran.set(true);
                latch.countDown();
            });
            latch.await();
            assertThat(ran).isTrue();
        }

        @Test
        @DisplayName("submitted callables return their result")
        void callableReturnsResult() throws Exception {
            Future<String> future = kafkaListenerPool.submit(() -> "hello-kafka");
            assertThat(future.get()).isEqualTo("hello-kafka");
        }
    }

    // -----------------------------------------------------------------------
    // orchestrationPool
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("orchestrationPool bean")
    class OrchestrationPoolTests {

        @Test
        @DisplayName("bean is non-null and is a ThreadPoolTaskExecutor")
        void beanCreated() {
            assertThat(orchestrationPool).isNotNull();
        }

        @Test
        @DisplayName("core pool size equals app.execution.orchestration-threads (6)")
        void corePoolSize() {
            assertThat(orchestrationPool.getCorePoolSize()).isEqualTo(6);
        }

        @Test
        @DisplayName("max pool size is double the core pool size (12)")
        void maxPoolSize() {
            assertThat(orchestrationPool.getMaxPoolSize()).isEqualTo(12);
        }

        @Test
        @DisplayName("queue capacity is 500")
        void queueCapacity() {
            assertThat(orchestrationPool.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(500);
        }

        @Test
        @DisplayName("thread name prefix is 'orchestration-'")
        void threadNamePrefix() {
            assertThat(orchestrationPool.getThreadNamePrefix()).isEqualTo("orchestration-");
        }

        @Test
        @DisplayName("submitted tasks actually execute")
        void tasksExecute() throws Exception {
            AtomicBoolean ran = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            orchestrationPool.execute(() -> {
                ran.set(true);
                latch.countDown();
            });
            latch.await();
            assertThat(ran).isTrue();
        }

        @Test
        @DisplayName("submitted callables return their result")
        void callableReturnsResult() throws Exception {
            Future<String> future = orchestrationPool.submit(() -> "hello-orchestration");
            assertThat(future.get()).isEqualTo("hello-orchestration");
        }
    }
}
