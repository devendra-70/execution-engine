package com.epam.execution_engine_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @EnableScheduling removed — no @Scheduled methods exist in this service
@SpringBootApplication
public class ExecutionEngineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutionEngineServiceApplication.class, args);
    }

}