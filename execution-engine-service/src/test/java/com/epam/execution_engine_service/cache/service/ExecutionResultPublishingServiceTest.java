package com.epam.execution_engine_service.cache.service;

import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.mapper.ResultMapper;
import com.epam.execution_engine_service.persistence.service.ExecutionResultPublishingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExecutionResultPublishingService.
 * Covers Redis KV cache, Pub/Sub publishing, and error handling.
 *
 * Test cases: 16+
 * 
 * DISABLED: Legacy test class superseded by equivalent tests in persistence.service package.
 * See: ExecutionResultPublishingServiceTest in com.epam.execution_engine_service.persistence.service
 */
@Disabled("Legacy tests superseded by persistence package tests")
@DisplayName("ExecutionResultPublishingService Tests")
@ExtendWith(MockitoExtension.class)
class ExecutionResultPublishingServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ResultMapper resultMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExecutionResultPublishingService service;

    private SubmissionEntity entity;
    private ExecutionResultEvent event;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        executionId = UUID.randomUUID();
        entity = SubmissionEntity.builder()
            .id(1L)
            .executionId(executionId)
            .userId(123L)
            .problemId(456L)
            .language("JAVA")
            .mode("SUBMIT")
            .verdict("PASSED")
            .status("COMPLETED")
            .score(100.0)
            .totalRuntimeMs(1500L)
            .memoryBytes(2048000L)
            .rawOutput("Output")
            .submittedCode("code")
            .submittedAt(OffsetDateTime.now().minusMinutes(5))
            .completedAt(OffsetDateTime.now())
            .testResults(new ArrayList<>())
            .build();

        event = ExecutionResultEvent.builder()
            .executionId(executionId)
            .userId(123L)
            .problemId(456L)
            .language("JAVA")
            .mode("SUBMIT")
            .verdict("PASSED")
            .status("COMPLETED")
            .score(100.0)
            .totalRuntimeMs(1500L)
            .memoryBytes(2048000L)
            .rawOutput("Output")
            .submittedCode("code")
            .submittedAt(OffsetDateTime.now().minusMinutes(5))
            .completedAt(OffsetDateTime.now())
            .testResults(new ArrayList<>())
            .build();

        // Set default TTL
        ReflectionTestUtils.setField(service, "statusTtlSeconds", 600L);
    }

    @Nested
    @DisplayName("Redis Publishing Operations")
    class PublishingTests {

        @Test
        @DisplayName("Should publish execution result to Redis KV and Pub/Sub")
        void testPublishExecutionResult() throws Exception {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(resultMapper.toExecutionResultEvent(entity)).thenReturn(event);
            when(objectMapper.writeValueAsString(event)).thenReturn("{\"executionId\":\"" + executionId + "\"}");

            // Act
            service.publishExecutionResult(entity);

            // Assert
            verify(redisTemplate, atLeastOnce()).opsForValue();
            verify(valueOps, times(1)).set(
                String.format("execution:status:%s", executionId),
                "{\"executionId\":\"" + executionId + "\"}",
                600L,
                TimeUnit.SECONDS
            );
            verify(redisTemplate, times(1)).convertAndSend("execution-completed", "{\"executionId\":\"" + executionId + "\"}");
        }

        @Test
        @DisplayName("Should use correct Redis KV key format")
        void testRedisKeyFormat() throws Exception {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(resultMapper.toExecutionResultEvent(entity)).thenReturn(event);
            when(objectMapper.writeValueAsString(event)).thenReturn("{}");

            // Act
            service.publishExecutionResult(entity);

            // Assert
            String expectedKey = String.format("execution:status:%s", executionId);
            verify(valueOps, times(1)).set(
                expectedKey,
                "{}",
                600L,
                TimeUnit.SECONDS
            );
        }

        @Test
        @DisplayName("Should use configured TTL from properties")
        void testConfiguredTtl() throws Exception {
            // Arrange
            ReflectionTestUtils.setField(service, "statusTtlSeconds", 1200L);  // 20 minutes
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(resultMapper.toExecutionResultEvent(entity)).thenReturn(event);
            when(objectMapper.writeValueAsString(event)).thenReturn("{}");

            // Act
            service.publishExecutionResult(entity);

            // Assert
            verify(valueOps, times(1)).set(
                anyString(),
                anyString(),
                eq(1200L),
                eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("Should publish to execution-completed Pub/Sub channel")
        void testPubSubChannelName() throws Exception {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(resultMapper.toExecutionResultEvent(entity)).thenReturn(event);
            when(objectMapper.writeValueAsString(event)).thenReturn("{}");

            // Act
            service.publishExecutionResult(entity);

            // Assert
            verify(redisTemplate, times(1)).convertAndSend("execution-completed", "{}");
        }

        @Test
        @DisplayName("Should serialize entity to JSON before publishing")
        void testSerializationToJson() throws Exception {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(resultMapper.toExecutionResultEvent(entity)).thenReturn(event);
            String jsonPayload = "{\"executionId\":\"" + executionId + "\"}";
            when(objectMapper.writeValueAsString(event)).thenReturn(jsonPayload);

            // Act
            service.publishExecutionResult(entity);

            // Assert
            verify(objectMapper, times(1)).writeValueAsString(event);
            verify(valueOps, times(1)).set(anyString(), eq(jsonPayload), anyLong(), any());
            verify(redisTemplate, times(1)).convertAndSend("execution-completed", jsonPayload);
        }
    }

    @Nested
    @DisplayName("Retrieval Operations")
    class RetrievalTests {

        @Test
        @DisplayName("Should retrieve execution result from cache")
        void testGetExecutionResult() throws Exception {
            // Arrange
            String jsonPayload = "{\"executionId\":\"" + executionId + "\"}";
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(String.format("execution:status:%s", executionId)))
                .thenReturn(jsonPayload);
            when(objectMapper.readValue(jsonPayload, ExecutionResultEvent.class))
                .thenReturn(event);

            // Act
            ExecutionResultEvent result = service.getExecutionResult(executionId);

            // Assert
            assertNotNull(result);
            assertEquals(executionId, result.getExecutionId());
        }

        @Test
        @DisplayName("Should return null for cache miss")
        void testGetExecutionResultCacheMiss() {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(String.format("execution:status:%s", executionId)))
                .thenReturn(null);

            // Act
            ExecutionResultEvent result = service.getExecutionResult(executionId);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null on deserialization error")
        void testGetExecutionResultDeserializationError() throws Exception {
            // Arrange
            String invalidJson = "not valid json";
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(String.format("execution:status:%s", executionId)))
                .thenReturn(invalidJson);
            when(objectMapper.readValue(invalidJson, ExecutionResultEvent.class))
                .thenThrow(new Exception("Parse error"));

            // Act
            ExecutionResultEvent result = service.getExecutionResult(executionId);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("Should throw exception for null executionId in get")
        void testGetExecutionResultNullIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.getExecutionResult(null));
        }
    }

    @Nested
    @DisplayName("Cleanup Operations")
    class CleanupTests {

        @Test
        @DisplayName("Should clear execution result from cache")
        void testClearExecutionResult() {
            // Arrange
            when(redisTemplate.delete(String.format("execution:status:%s", executionId)))
                .thenReturn(true);

            // Act
            service.clearExecutionResult(executionId);

            // Assert
            verify(redisTemplate, times(1)).delete(String.format("execution:status:%s", executionId));
        }

        @Test
        @DisplayName("Should use correct key format for deletion")
        void testClearKeyFormat() {
            // Arrange
            when(redisTemplate.delete(anyString())).thenReturn(true);

            // Act
            service.clearExecutionResult(executionId);

            // Assert
            String expectedKey = String.format("execution:status:%s", executionId);
            verify(redisTemplate, times(1)).delete(expectedKey);
        }

        @Test
        @DisplayName("Should handle deletion failure gracefully")
        void testClearExecutionResultFailureLogged() {
            // Arrange
            when(redisTemplate.delete(anyString()))
                .thenThrow(new RuntimeException("Redis unavailable"));

            // Act & Assert (should not throw)
            assertDoesNotThrow(() -> service.clearExecutionResult(executionId));
        }

        @Test
        @DisplayName("Should throw exception for null executionId in clear")
        void testClearExecutionResultNullIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.clearExecutionResult(null));
        }
    }

    @Nested
    @DisplayName("Error Handling (Non-Blocking)")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should not propagate Redis errors (non-blocking per SRS §8)")
        void testRedisErrorsNotPropagated() throws Exception {
            // Arrange
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection failed"));
            when(resultMapper.toExecutionResultEvent(entity)).thenReturn(event);

            // Act & Assert (should not throw)
            assertDoesNotThrow(() -> service.publishExecutionResult(entity));
        }

        @Test
        @DisplayName("Should not propagate serialization errors")
        void testSerializationErrorsNotPropagated() throws Exception {
            // Arrange
            when(resultMapper.toExecutionResultEvent(entity)).thenReturn(event);
            when(objectMapper.writeValueAsString(event))
                .thenThrow(new Exception("Serialization failed"));

            // Act & Assert (should not throw)
            assertDoesNotThrow(() -> service.publishExecutionResult(entity));
        }

        @Test
        @DisplayName("Should throw exception for null entity")
        void testNullEntityThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.publishExecutionResult(null));
        }

        @Test
        @DisplayName("Should throw exception for null executionId in entity")
        void testNullExecutionIdInEntityThrows() {
            entity.setExecutionId(null);
            assertThrows(IllegalArgumentException.class,
                () -> service.publishExecutionResult(entity));
        }
    }

    @Nested
    @DisplayName("SRS §8 Compliance")
    class SrsComplianceTests {

        @Test
        @DisplayName("Should implement Redis KV pattern per SRS §8")
        void testRedisSrs8KvPattern() throws Exception {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(resultMapper.toExecutionResultEvent(entity)).thenReturn(event);
            when(objectMapper.writeValueAsString(event)).thenReturn("{}");

            // Act
            service.publishExecutionResult(entity);

            // Assert
            // Verify KV pattern: execution:status:{executionId}
            verify(valueOps, times(1)).set(
                startsWith("execution:status:"),
                eq("{}"),
                eq(600L),
                eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("Should implement Redis Pub/Sub pattern per SRS §8")
        void testRedisSrs8PubSubPattern() throws Exception {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(resultMapper.toExecutionResultEvent(entity)).thenReturn(event);
            when(objectMapper.writeValueAsString(event)).thenReturn("{}");

            // Act
            service.publishExecutionResult(entity);

            // Assert
            // Verify Pub/Sub channel: execution-completed
            verify(redisTemplate, times(1)).convertAndSend("execution-completed", "{}");
        }
    }
}
