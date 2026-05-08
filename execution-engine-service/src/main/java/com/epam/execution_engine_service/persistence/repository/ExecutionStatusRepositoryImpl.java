package com.epam.execution_engine_service.persistence.repository;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.persistence.entity.ExecutionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * Redis implementation of ExecutionStatusRepository
 * Stores ExecutionStatus in Redis with configurable TTL
 */
@Repository
public class ExecutionStatusRepositoryImpl implements ExecutionStatusRepository {
    
    private static final String KEY_PREFIX = "execution:status:";
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;
    
    /**
     * Constructor to initialize ObjectMapper with JavaTimeModule for Instant/LocalDateTime serialization
     * Called by Spring dependency injection
     */
    public ExecutionStatusRepositoryImpl(RedisTemplate<String, String> redisTemplate, 
                                        ApplicationProperties applicationProperties) {
        this.redisTemplate = redisTemplate;
        this.applicationProperties = applicationProperties;
        this.objectMapper = new ObjectMapper();
        // Register JavaTimeModule to handle Instant, LocalDateTime, etc. (SRS Section 3.4)
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public ExecutionStatus save(ExecutionStatus status) {
        try {
            String key = KEY_PREFIX + status.getExecutionId();
            String value = objectMapper.writeValueAsString(status);
            
            int ttlSeconds = applicationProperties.getRedis().getStatusTtlSeconds();
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            
            return status;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save ExecutionStatus to Redis", e);
        }
    }
    
    @Override
    public ExecutionStatus findById(String executionId) {
        try {
            String key = KEY_PREFIX + executionId;
            String value = redisTemplate.opsForValue().get(key);
            
            if (value == null) {
                return null;
            }
            
            return objectMapper.readValue(value, ExecutionStatus.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read ExecutionStatus from Redis", e);
        }
    }
    
    @Override
    public void deleteById(String executionId) {
        String key = KEY_PREFIX + executionId;
        redisTemplate.delete(key);
    }
    
    @Override
    public boolean exists(String executionId) {
        String key = KEY_PREFIX + executionId;
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }
}
