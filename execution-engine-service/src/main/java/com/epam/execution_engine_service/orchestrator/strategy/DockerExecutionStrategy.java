package com.epam.execution_engine_service.orchestrator.strategy;

import com.epam.execution_engine_service.domain.TestCase;
import com.epam.execution_engine_service.domain.TestCaseResultEvent;
import com.epam.execution_engine_service.orchestrator.docker.ContainerPool;
import com.epam.execution_engine_service.orchestrator.docker.SandboxContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SRP/DIP: Handles real Docker-based execution.
 * Depends on {@link ContainerPool} interface, not the concrete Docker implementation.
 * Marked {@code @Order(2)} — evaluated after the stub strategy.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DockerExecutionStrategy implements CodeExecutionStrategy {

    private final ContainerPool containerPool;

    @Override
    public boolean supports(boolean dockerAvailable) {
        return dockerAvailable;
    }

    @Override
    public List<TestCaseResultEvent> execute(String sourceCode, List<TestCase> testCases, long timeoutMs) throws Exception {
        log.info("[DockerExecution] Acquiring sandbox container (pool size={})", containerPool.getPoolSize());
        SandboxContainer container = containerPool.acquire(timeoutMs + 5000);
        log.info("[DockerExecution] Acquired container {}", container.getContainerId());
        try {
            return container.execute(sourceCode, testCases, timeoutMs);
        } finally {
            containerPool.release(container);
            log.info("[DockerExecution] Released container {} (pool size={})",
                    container.getContainerId(), containerPool.getPoolSize());
        }
    }
}



