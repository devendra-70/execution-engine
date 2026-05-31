package com.epam.execution_engine_service.orchestrator.docker;

/**
 * DIP/ISP: Abstraction over the sandbox container pool.
 * Consumers depend on this interface, not the Docker-specific implementation.
 */
public interface ContainerPool {

    /**
     * Acquire a ready sandbox container, blocking up to {@code timeoutMs} milliseconds.
     *
     * @throws Exception if no container becomes available within the timeout
     */
    SandboxContainer acquire(long timeoutMs) throws Exception;

    /**
     * Return a container to the pool after use.
     * Must always be called from a finally block.
     */
    void release(SandboxContainer container);

    /**
     * Returns {@code true} when a real Docker daemon is reachable.
     */
    boolean isDockerAvailable();

    /**
     * Approximate number of idle containers currently in the pool.
     */
    int getPoolSize();
}

