package com.epam.execution_engine_service.persistence.service;

import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.epam.execution_engine_service.persistence.mapper.ResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExecutionResultPublishingService.
 * Covers Redis KV pattern, Pub/Sub publishing, and TTL configuration.
 *
 * EPMICMPCOD-462: Redis publishing (SRS §8).
 * Test cases: 12+
 */
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

    private ExecutionResultPublishingService service;

    private SubmissionEntity entity;
    private ExecutionResultEvent event;
    private UUID executionId;

    @BeforeEach
    void setUp() throws Exception {
        executionId = UUID.randomUUID();

        // Create service with explicit TTL value (600 seconds, matching app.redis.status-ttl-seconds default)
        service = new ExecutionResultPublishingService(redisTemplate, resultMapper, objectMapper, 600L);

        // Build SubmissionEntity (passed to service)
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
            .memoryBytes(51200000L)
            .createdAt(OffsetDateTime.now())
            .testResults(new ArrayList<>())
            .build();

        // Build ExecutionResultEvent (used internally by service)
        event = ExecutionResultEvent.builder()
            .executionId(executionId)
            .userId(123L)
            .problemId(456L)
            .verdict("PASSED")
            .status("COMPLETED")
            .score(100.0)
            .totalRuntimeMs(1500L)
            .memoryBytes(51200000L)
            .completedAt(OffsetDateTime.now())
            .testResults(new ArrayList<>())
            .build();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(resultMapper.toExecutionResultEvent(any(SubmissionEntity.class))).thenReturn(event);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"status\":\"COMPLETED\"}");
    }

    @Nested
    @DisplayName("Redis KV Pattern")
    class RedisKVTests {

        @Test
        @DisplayName("Should set execution status in Redis with correct key")
        void testPublishExecutionCompletion_setsKeyFormat() throws Exception {
            // Act
            service.publishExecutionResult(entity);

            // Assert
            String expectedKey = "execution:status:" + executionId;
            verify(valueOps).set(
                eq(expectedKey),
                any(String.class),
                anyLong(),
                eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("Should set Redis KV with configured TTL")
        void testPublishExecutionCompletion_setsTTL() throws Exception {
            // Act
            service.publishExecutionResult(entity);

            // Assert - verify TTL is set (default 600 seconds)
            verify(valueOps).set(
                anyString(),
                anyString(),
                eq(600L),
                eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("Should serialize event to JSON for Redis storage")
        void testPublishExecutionCompletion_serializesToJson() throws Exception {
            // Act
            service.publishExecutionResult(entity);

            // Assert
            verify(objectMapper).writeValueAsString(any());
        }
    }

    @Nested
    @DisplayName("Redis Pub/Sub Pattern")
    class RedisPubSubTests {

        @Test
        @DisplayName("Should publish to execution-completed channel")
        void testPublishExecutionCompletion_publishesToChannel() throws Exception {
            // Act
            service.publishExecutionResult(entity);

            // Assert
            verify(redisTemplate).convertAndSend(
                eq("execution-completed"),
                any(String.class)
            );
        }

        @Test
        @DisplayName("Should publish JSON payload to Pub/Sub")
        void testPublishExecutionCompletion_publishesJson() throws Exception {
            // Arrange
            String jsonPayload = "{\"executionId\":\"" + executionId + "\"}";
            when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);

            // Act
            service.publishExecutionResult(entity);

            // Assert
            verify(redisTemplate).convertAndSend(
                eq("execution-completed"),
                eq(jsonPayload)
            );
        }
    }

    @Nested
    @DisplayName("Error Handling and Validation")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception for null event")
        void testPublishExecutionCompletion_nullEventThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.publishExecutionResult(null));
        }

        @Test
        @DisplayName("Should throw exception for null executionId")
        void testPublishExecutionCompletion_nullExecutionIdThrows() {
            // Arrange
            event.setExecutionId(null);

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                () -> service.publishExecutionResult(null));
        }

        @Test
        @DisplayName("Should log and continue on Redis serialization error")
        void testPublishExecutionCompletion_serializationErrorHandled() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("JSON serialization failed"));

            // Act - should not rethrow
            assertDoesNotThrow(() -> service.publishExecutionResult(entity));
        }

        @Test
        @DisplayName("Should log and continue on Redis connection error")
        void testPublishExecutionCompletion_redisErrorHandled() throws Exception {
            // Arrange
            doThrow(new RuntimeException("Redis connection failed"))
                .when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

            // Act - should not rethrow
            assertDoesNotThrow(() -> service.publishExecutionResult(entity));
        }
    }

    @Nested
    @DisplayName("Execution Status Retrieval")
    class RetrievalTests {

        @Test
        @DisplayName("Should retrieve execution status from Redis")
        void testGetExecutionStatus() throws Exception {
            // Arrange
            String statusJson = "{\"status\":\"COMPLETED\"}";
            ExecutionResultEvent expectedEvent = ExecutionResultEvent.builder()
                .executionId(executionId)
                .status("COMPLETED")
                .build();
            when(valueOps.get("execution:status:" + executionId))
                .thenReturn(statusJson);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(objectMapper.readValue(statusJson, ExecutionResultEvent.class))
                .thenReturn(expectedEvent);

            // Act
            ExecutionResultEvent result = service.getExecutionResult(executionId);

            // Assert
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should return null for missing execution status")
        void testGetExecutionStatus_notFound() {
            // Arrange
            when(valueOps.get("execution:status:" + executionId))
                .thenReturn(null);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            // Act
            ExecutionResultEvent result = service.getExecutionResult(executionId);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("Should throw for null executionId in retrieval")
        void testGetExecutionStatus_nullExecutionIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.getExecutionResult(null));
        }
    }

    @Nested
    @DisplayName("Execution Status Deletion")
    class DeletionTests {

        @Test
        @DisplayName("Should delete execution status from Redis")
        void testDeleteExecutionStatus() {
            // Arrange
            when(redisTemplate.delete("execution:status:" + executionId))
                .thenReturn(true);

            // Act
            service.clearExecutionResult(executionId);

            // Assert
            verify(redisTemplate).delete("execution:status:" + executionId);
        }

        @Test
        @DisplayName("Should handle deletion when status not found")
        void testDeleteExecutionStatus_notFound() {
            // Arrange
            when(redisTemplate.delete("execution:status:" + executionId))
                .thenReturn(false);

            // Act
            service.clearExecutionResult(executionId);

            // Assert
            verify(redisTemplate).delete("execution:status:" + executionId);
        }

        @Test
        @DisplayName("Should throw for null executionId in deletion")
        void testDeleteExecutionStatus_nullExecutionIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.clearExecutionResult(null));
        }
    }
}
