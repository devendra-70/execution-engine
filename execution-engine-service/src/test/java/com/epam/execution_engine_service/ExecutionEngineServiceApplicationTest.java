package com.epam.execution_engine_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionEngineServiceApplication — Unit Tests")
class ExecutionEngineServiceApplicationTest {

    @Test
    @DisplayName("Class is annotated with @SpringBootApplication")
    void hasSpringBootApplicationAnnotation() {
        assertThat(ExecutionEngineServiceApplication.class
                .isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
    }

    @Test
    @DisplayName("Class is annotated with @EnableScheduling")
    void hasEnableSchedulingAnnotation() {
        assertThat(ExecutionEngineServiceApplication.class
                .isAnnotationPresent(EnableScheduling.class))
                .isTrue();
    }

    @Test
    @DisplayName("Application class can be instantiated")
    void canBeInstantiated() {
        assertThat(new ExecutionEngineServiceApplication()).isNotNull();
    }
}
