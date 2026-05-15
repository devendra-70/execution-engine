package com.epam.execution_engine_service.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolConfig {

    @Value("${app.kafka.concurrency:25}")
    private int kafkaConcurrency;

    @Value("${app.execution.orchestration-threads:50}")
    private int orchestrationThreads;

    @Bean(name = "kafkaListenerPool")
    public ThreadPoolTaskExecutor kafkaListenerPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(kafkaConcurrency);
        executor.setMaxPoolSize(kafkaConcurrency * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("kafka-listener-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "orchestrationPool")
    public ThreadPoolTaskExecutor orchestrationPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(orchestrationThreads);
        executor.setMaxPoolSize(orchestrationThreads * 2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("orchestration-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}

