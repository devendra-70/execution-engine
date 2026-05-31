package com.epam.execution_engine_service.orchestrator.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/**
 * SRP: Single responsibility — create and configure a DockerClient.
 * Extracted from DockerContainerPool so the pool is not responsible for client wiring.
 */
@Slf4j
@Component
public class DockerClientFactory {

    @Value("${app.docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    /**
     * Build and return a connected {@link DockerClient}.
     *
     * @throws Exception if Docker is not reachable
     */
    public DockerClient createClient() throws Exception {
        DefaultDockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerHost))
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        DockerClient client = DockerClientImpl.getInstance(config, httpClient);
        client.pingCmd().exec(); // throws if Docker is unreachable
        log.info("[DockerClientFactory] Docker connected at {}", dockerHost);
        return client;
    }
}

