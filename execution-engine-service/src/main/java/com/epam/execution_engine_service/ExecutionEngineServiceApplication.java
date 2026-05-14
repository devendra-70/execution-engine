package com.epam.execution_engine_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExecutionEngineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutionEngineServiceApplication.class, args);
    }

}