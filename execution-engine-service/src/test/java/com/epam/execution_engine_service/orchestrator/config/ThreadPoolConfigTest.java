package com.epam.execution_engine_service.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolConfigTest {

    private ThreadPoolConfig threadPoolConfig;

    @BeforeEach
    void setUp() {
        threadPoolConfig = new ThreadPoolConfig();
    }

    @Test
    void testKafkaListenerPoolCreation() {
        ReflectionTestUtils.setField(threadPoolConfig, "kafkaConcurrency", 25);

        ThreadPoolTaskExecutor pool = threadPoolConfig.kafkaListenerPool();

        assertNotNull(pool, "Kafka listener pool should be created");
    }

    @Test
    void testKafkaListenerPoolConfiguration() {
        ReflectionTestUtils.setField(threadPoolConfig, "kafkaConcurrency", 25);

        ThreadPoolTaskExecutor pool = threadPoolConfig.kafkaListenerPool();

        assertNotNull(pool, "Kafka listener pool should be created");
        assertEquals(25, pool.getCorePoolSize(), "Core pool size should match kafka concurrency");
        assertEquals(50, pool.getMaxPoolSize(), "Max pool size should be double the core pool size");
        assertEquals(100, pool.getQueueCapacity(), "Queue capacity should be 100");
        assertEquals("kafka-listener-", pool.getThreadNamePrefix(), "Thread name prefix should be kafka-listener-");
    }

    @Test
    void testKafkaListenerPoolShutdownConfiguration() {
        ReflectionTestUtils.setField(threadPoolConfig, "kafkaConcurrency", 25);

        ThreadPoolTaskExecutor pool = threadPoolConfig.kafkaListenerPool();

        assertNotNull(pool, "Kafka listener pool should be created");
        assertTrue(pool.getWaitForTasksToCompleteOnShutdown(),
                   "Should wait for tasks to complete on shutdown");
        assertEquals(30, pool.getAwaitTerminationSeconds(),
                     "Await termination seconds should be 30");
    }

    @Test
    void testOrchestrationPoolCreation() {
        ReflectionTestUtils.setField(threadPoolConfig, "orchestrationThreads", 50);

        ThreadPoolTaskExecutor pool = threadPoolConfig.orchestrationPool();

        assertNotNull(pool, "Orchestration pool should be created");
    }

    @Test
    void testOrchestrationPoolConfiguration() {
        ReflectionTestUtils.setField(threadPoolConfig, "orchestrationThreads", 50);

        ThreadPoolTaskExecutor pool = threadPoolConfig.orchestrationPool();

        assertNotNull(pool, "Orchestration pool should be created");
        assertEquals(50, pool.getCorePoolSize(), "Core pool size should match orchestration threads");
        assertEquals(100, pool.getMaxPoolSize(), "Max pool size should be double the core pool size");
        assertEquals(500, pool.getQueueCapacity(), "Queue capacity should be 500");
        assertEquals("orchestration-", pool.getThreadNamePrefix(), "Thread name prefix should be orchestration-");
    }

    @Test
    void testOrchestrationPoolShutdownConfiguration() {
        ReflectionTestUtils.setField(threadPoolConfig, "orchestrationThreads", 50);

        ThreadPoolTaskExecutor pool = threadPoolConfig.orchestrationPool();

        assertNotNull(pool, "Orchestration pool should be created");
        assertTrue(pool.getWaitForTasksToCompleteOnShutdown(),
                   "Should wait for tasks to complete on shutdown");
        assertEquals(60, pool.getAwaitTerminationSeconds(),
                     "Await termination seconds should be 60");
    }

    @Test
    void testKafkaPoolQueueCapacity() {
        ReflectionTestUtils.setField(threadPoolConfig, "kafkaConcurrency", 25);

        ThreadPoolTaskExecutor pool = threadPoolConfig.kafkaListenerPool();

        assertEquals(100, pool.getQueueCapacity(), "Kafka pool queue capacity should be 100");
    }

    @Test
    void testOrchestrationPoolQueueCapacity() {
        ReflectionTestUtils.setField(threadPoolConfig, "orchestrationThreads", 50);

        ThreadPoolTaskExecutor pool = threadPoolConfig.orchestrationPool();

        assertEquals(500, pool.getQueueCapacity(), "Orchestration pool queue capacity should be 500");
    }

    @Test
    void testThreadPoolConfigInstantiation() {
        ThreadPoolConfig config = new ThreadPoolConfig();
        assertNotNull(config, "ThreadPoolConfig should be instantiable");
    }

    @Test
    void testCustomConcurrencyValues() {
        ReflectionTestUtils.setField(threadPoolConfig, "kafkaConcurrency", 10);

        ThreadPoolTaskExecutor pool = threadPoolConfig.kafkaListenerPool();

        assertEquals(10, pool.getCorePoolSize(), "Core pool size should be custom value");
        assertEquals(20, pool.getMaxPoolSize(), "Max pool size should be double of core");
    }

    @Test
    void testCustomOrchestrationValues() {
        ReflectionTestUtils.setField(threadPoolConfig, "orchestrationThreads", 100);

        ThreadPoolTaskExecutor pool = threadPoolConfig.orchestrationPool();

        assertEquals(100, pool.getCorePoolSize(), "Core pool size should be custom value");
        assertEquals(200, pool.getMaxPoolSize(), "Max pool size should be double of core");
    }
}



