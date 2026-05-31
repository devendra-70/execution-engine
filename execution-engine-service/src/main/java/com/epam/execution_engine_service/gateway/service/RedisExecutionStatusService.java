package com.epam.execution_engine_service.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * SRP: Single responsibility — look up execution status from Redis.
 */
@Service
@RequiredArgsConstructor
public class RedisExecutionStatusService implements ExecutionStatusService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> getStatus(String executionId) {
        String key = "execution:status:" + executionId;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }
}

