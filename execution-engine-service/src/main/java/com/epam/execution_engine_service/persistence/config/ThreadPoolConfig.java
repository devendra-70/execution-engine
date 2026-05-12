package com.epam.execution_engine_service.persistence.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * ThreadPoolConfig — Thread pool configuration (SRS §4.1)
 * 
 * Configures two independent thread pools:
 * - Pool A (Kafka Listener): app.kafka.concurrency threads
 * - Pool B (TaskExecutor): app.execution.orchestration-threads threads
 * 
 * Pool isolation prevents Kafka consumer from blocking on I/O operations.
 */
@Configuration
@Slf4j
public class ThreadPoolConfig {

    @Value("${app.execution.orchestration-threads:50}")
    private int orchestrationThreads;

    @Value("${app.execution.orchestration-queue-capacity:1000}")
    private int queueCapacity;

    /**
     * Pool B: TaskExecutor for orchestration tasks (SRS §4.1)
     * 
     * Configuration:
     * - Core threads: orchestration-threads
     * - Max threads: orchestration-threads * 2
     * - Queue capacity: orchestration-queue-capacity
     * - Rejection policy: CallerRunsPolicy
     * 
     * @return Executor bean named "taskExecutor"
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(orchestrationThreads);
        executor.setMaxPoolSize(orchestrationThreads * 2);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("orchestration-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        // CallerRunsPolicy: if queue is full, caller thread executes task
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        return executor;
    }

}
