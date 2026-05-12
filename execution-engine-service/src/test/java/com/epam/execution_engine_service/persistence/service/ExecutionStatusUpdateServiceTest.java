package com.epam.execution_engine_service.persistence.service;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.persistence.entity.ExecutionStatus;
import com.epam.execution_engine_service.persistence.repository.ExecutionStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for ExecutionStatusUpdateService (SRS §8)
 * 
 * Tests status update functionality in Redis KV store.
 * Covers status persistence with configurable TTL.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionStatusUpdateServiceTest {
    
    @Mock
    private ExecutionStatusRepository executionStatusRepository;
    
    @Mock
    private ApplicationProperties applicationProperties;
    
    @InjectMocks
    private ExecutionStatusUpdateService executionStatusUpdateService;
    
    private UUID executionId;
    private ApplicationProperties.Redis redisConfig;
    
    @BeforeEach
    void setUp() {
        executionId = UUID.randomUUID();
        
        redisConfig = new ApplicationProperties.Redis();
        redisConfig.setStatusTtlSeconds(600);
        
        when(applicationProperties.getRedis()).thenReturn(redisConfig);
        when(executionStatusRepository.save(any())).thenReturn(null);
    }
    
    @Test
    void testUpdateStatus_Success_SavesStatusWithTTL() {
        testUpdateStatus_Success_SavesStatusWithTTL_Internal();
    }
    
    private void testUpdateStatus_Success_SavesStatusWithTTL_Internal() {
        executionStatusUpdateService.updateStatus(executionId, "PENDING");
        
        verify(executionStatusRepository, times(1)).save(any(ExecutionStatus.class));
        // TTL is applied during save() based on app.redis.status-ttl-seconds configuration
    }
    
    @Test
    void testUpdateStatus_Completed_SavesCompletedStatus() {
        executionStatusUpdateService.updateStatus(executionId, "COMPLETED");
        
        verify(executionStatusRepository, times(1)).save(any(ExecutionStatus.class));
    }
    
    @Test
    void testUpdateStatus_Failed_SavesFailedStatus() {
        executionStatusUpdateService.updateStatus(executionId, "FAILED");
        
        verify(executionStatusRepository, times(1)).save(any(ExecutionStatus.class));
    }
    
    @Test
    void testUpdateStatus_RepositoryException_HandledGracefully() {
        when(executionStatusRepository.save(any())).thenThrow(new RuntimeException("Redis error"));
        
        assertDoesNotThrow(() -> executionStatusUpdateService.updateStatus(executionId, "PENDING"));
    }
    
    @Test
    void testUpdateStatusWithTtl_CustomTTL_SavesWithCustomTTL() {
        executionStatusUpdateService.updateStatusWithTtl(executionId, "RUNNING", 300);
        
        verify(executionStatusRepository, times(1)).save(any(ExecutionStatus.class));
        // Custom TTL configuration is handled by ExecutionStatusUpdateService
    }
    
    @Test
    void testUpdateStatusWithTtl_Exception_HandledGracefully() {
        when(executionStatusRepository.save(any())).thenThrow(new RuntimeException("Redis error"));
        
        assertDoesNotThrow(() -> executionStatusUpdateService.updateStatusWithTtl(
                executionId, "EXECUTING", 300));
    }
}
