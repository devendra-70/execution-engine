package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.gateway.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for RateLimiterService (SRS §3.5)
 * 
 * Tests rate limiting functionality per userId and IP address.
 * Covers token bucket algorithm implementation in Redis.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimiterServiceTest {
    
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ApplicationProperties applicationProperties;
    
    @InjectMocks
    private RateLimiterService rateLimiterService;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    private ApplicationProperties.RateLimit rateLimitConfig;
    
    @BeforeEach
    void setUp() {
        rateLimitConfig = new ApplicationProperties.RateLimit();
        rateLimitConfig.setRequestsPerMinute(5);
        rateLimitConfig.setWindowSeconds(60);
        
        when(applicationProperties.getRateLimit()).thenReturn(rateLimitConfig);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    }
    
    @Test
    void testCheckRateLimit_FirstRequest_Allowed() {
        when(valueOperations.get(contains("user:test-user"))).thenReturn(null);
        when(valueOperations.get(contains("ip:192.168.1.1"))).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        
        // Should not throw exception
        assertDoesNotThrow(() -> rateLimiterService.checkRateLimit("test-user", "192.168.1.1"));
    }
    
    @Test
    void testCheckRateLimit_WithinLimit_Allowed() {
        when(valueOperations.get(contains("user:test-user"))).thenReturn("2");
        when(valueOperations.get(contains("ip:192.168.1.1"))).thenReturn("2");
        when(valueOperations.increment(anyString())).thenReturn(3L);
        
        assertDoesNotThrow(() -> rateLimiterService.checkRateLimit("test-user", "192.168.1.1"));
    }
    
    @Test
    void testCheckRateLimit_ExceededForUser_ThrowsException() {
        when(valueOperations.get(contains("user:test-user"))).thenReturn("5");
        when(valueOperations.get(contains("ip:192.168.1.1"))).thenReturn("2");
        
        assertThrows(RateLimitException.class, 
                () -> rateLimiterService.checkRateLimit("test-user", "192.168.1.1"));
    }
    
    @Test
    void testCheckRateLimit_ExceededForIP_ThrowsException() {
        when(valueOperations.get(contains("user:test-user"))).thenReturn("2");
        when(valueOperations.get(contains("ip:192.168.1.1"))).thenReturn("5");
        
        assertThrows(RateLimitException.class, 
                () -> rateLimiterService.checkRateLimit("test-user", "192.168.1.1"));
    }
    
    @Test
    void testCheckRateLimit_RedisException_ThrowsRateLimitException() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));
        
        assertThrows(RateLimitException.class, 
                () -> rateLimiterService.checkRateLimit("test-user", "192.168.1.1"));
    }
    
    @Test
    void testCheckRateLimit_SetsTTLOnNewKey() {
        when(valueOperations.get(contains("user:test-user"))).thenReturn(null);
        when(valueOperations.get(contains("ip:192.168.1.1"))).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        
        rateLimiterService.checkRateLimit("test-user", "192.168.1.1");
        
        verify(redisTemplate, atLeastOnce()).expire(anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }
}
