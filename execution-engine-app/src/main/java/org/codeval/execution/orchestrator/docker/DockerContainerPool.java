package org.codeval.execution.orchestrator.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DockerContainerPool {

    @Value("${app.execution.pool.warm-min-size:5}")
    private int warmMinSize;

    @Value("${app.execution.pool.idle-ttl-seconds:300}")
    private long idleTtlSeconds;

    @Value("${app.execution.sandbox.memory-limit-mb:256}")
    private long memoryLimitMb;

    @Value("${app.execution.sandbox.jvm-xms:128m}")
    private String jvmXms;

    @Value("${app.execution.sandbox.jvm-xmx:256m}")
    private String jvmXmx;

    @Value("${app.docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    @Value("${app.docker.sandbox-image:codeval/sandbox-wrapper:latest}")
    private String sandboxImage;

    private DockerClient dockerClient;
    private final BlockingQueue<SandboxContainer> pool = new ArrayBlockingQueue<>(100);
    private final List<SandboxContainer> allContainers = new ArrayList<>();

    @PostConstruct
    public void initialize() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .build();
            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(URI.create(dockerHost))
                    .maxConnections(50)
                    .connectionTimeout(Duration.ofSeconds(10))
                    .responseTimeout(Duration.ofSeconds(30))
                    .build();
            dockerClient = DockerClientImpl.getInstance(config, httpClient);

            log.info("Pre-warming {} sandbox containers...", warmMinSize);
            for (int i = 0; i < warmMinSize; i++) {
                SandboxContainer container = createAndStartContainer();
                pool.offer(container);
                allContainers.add(container);
            }
            log.info("Container pool initialized with {} containers", warmMinSize);
        } catch (Exception e) {
            log.warn("Docker not available - container pool running in STUB mode: {}", e.getMessage());
        }
    }

    private SandboxContainer createAndStartContainer() {
        CreateContainerResponse container = dockerClient.createContainerCmd(sandboxImage)
                .withHostConfig(HostConfig.newHostConfig()
                        .withMemory(memoryLimitMb * 1024 * 1024)
                        .withMemorySwap(memoryLimitMb * 1024 * 1024))
                .withEnv(
                        "JAVA_OPTS=-Xms" + jvmXms + " -Xmx" + jvmXmx
                )
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();
        log.debug("Started sandbox container: {}", container.getId());
        return new SandboxContainer(container.getId(), dockerClient);
    }

    public SandboxContainer acquire(long timeoutMs) throws InterruptedException {
        SandboxContainer container = pool.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (container == null) {
            log.warn("No container available in pool within timeout, creating a new one");
            container = createAndStartContainer();
        }
        return container;
    }

    public void release(SandboxContainer container) {
        if (container.isHealthy()) {
            pool.offer(container);
        } else {
            log.warn("Container {} is unhealthy, replacing with a fresh one", container.getContainerId());
            try {
                dockerClient.removeContainerCmd(container.getContainerId()).withForce(true).exec();
                SandboxContainer fresh = createAndStartContainer();
                pool.offer(fresh);
                allContainers.add(fresh);
            } catch (Exception e) {
                log.error("Failed to replace container", e);
            }
        }
    }

    public boolean isDockerAvailable() {
        return dockerClient != null;
    }

    @PreDestroy
    public void shutdown() {
        if (dockerClient != null) {
            allContainers.forEach(c -> {
                try {
                    dockerClient.stopContainerCmd(c.getContainerId()).exec();
                    dockerClient.removeContainerCmd(c.getContainerId()).exec();
                } catch (Exception e) {
                    log.warn("Error stopping container {}", c.getContainerId());
                }
            });
        }
    }
}

