package com.epam.execution_engine_service.gateway.rest;

import com.epam.execution_engine_service.domain.Verdict;
import com.epam.execution_engine_service.gateway.rest.dev.DevInspectController;
import com.epam.execution_engine_service.gateway.security.JwtTokenValidator;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.entity.SubmissionTestResultEntity;
import com.epam.execution_engine_service.persistence.repository.SubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DevInspectController.class)
@ActiveProfiles("dev")
@DisplayName("DevInspectController Unit Tests")
class DevInspectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenValidator jwtTokenValidator;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private SubmissionRepository submissionRepository;

    @Nested
    @DisplayName("Redis Status Endpoint Tests")
    class RedisStatusTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with exists=true when Redis key exists")
        void testRedisStatus_KeyExists() throws Exception {
            String executionId = "exec-123";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("PENDING");
            when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(300L);

            mockMvc.perform(get("/api/dev/inspect/redis/{executionId}", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true))
                    .andExpect(jsonPath("$.value").value("PENDING"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with exists=false when Redis key not found")
        void testRedisStatus_KeyNotFound() throws Exception {
            String executionId = "exec-missing";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn(null);

            mockMvc.perform(get("/api/dev/inspect/redis/{executionId}", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }

        @Test
        @WithMockUser
        @DisplayName("Should handle null TTL response from Redis")
        void testRedisStatus_NullTTL() throws Exception {
            String executionId = "exec-ttl";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("RUNNING");
            when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(null);

            mockMvc.perform(get("/api/dev/inspect/redis/{executionId}", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }
    }

    @Nested
    @DisplayName("Redis Rate Limit Endpoint Tests")
    class RedisRateLimitTests {

        @Test
        @WithMockUser
        @DisplayName("Should return rate limit keys for user")
        void testRedisRateLimit_WithKeys() throws Exception {
            String userId = "user-123";
            
            mockMvc.perform(get("/api/dev/inspect/redis/rate/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId))
                    .andExpect(jsonPath("$.keys").isArray());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return empty keys when no rate limit data exists")
        void testRedisRateLimit_NoKeys() throws Exception {
            String userId = "user-empty";
            
            mockMvc.perform(get("/api/dev/inspect/redis/rate/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.keys").isArray());
        }
    }

    @Nested
    @DisplayName("Database Submission Endpoint Tests")
    class DatabaseSubmissionTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 400 for invalid UUID format")
        void testDbSubmission_InvalidUUID() throws Exception {
            String invalidId = "not-a-uuid";
            
            mockMvc.perform(get("/api/dev/inspect/db/{executionId}", invalidId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with found=false when submission not in database")
        void testDbSubmission_NotFound() throws Exception {
            String executionId = "550e8400-e29b-41d4-a716-446655440000";
            
            mockMvc.perform(get("/api/dev/inspect/db/{executionId}", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.found").value(false));
        }
    }

    @Nested
    @DisplayName("Recent Submissions Endpoint Tests")
    class RecentSubmissionsTests {

        @Test
        @WithMockUser
        @DisplayName("Should return recent submissions list")
        void testRecentSubmissions_Success() throws Exception {
            mockMvc.perform(get("/api/dev/inspect/db/recent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser
        @DisplayName("Should respect limit parameter")
        void testRecentSubmissions_WithLimit() throws Exception {
            mockMvc.perform(get("/api/dev/inspect/db/recent").param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return submissions sorted by submittedAt descending")
        void testRecentSubmissions_WithData_ReturnsEntries() throws Exception {
            SubmissionEntity s1 = buildSubmission(UUID.randomUUID(), 1L, 101L, "java",
                    Verdict.ACCEPTED, 100, Instant.parse("2024-01-01T10:00:00Z"));
            SubmissionEntity s2 = buildSubmission(UUID.randomUUID(), 2L, 102L, "python",
                    Verdict.WRONG_ANSWER, 50, Instant.parse("2024-01-02T10:00:00Z"));

            when(submissionRepository.findAll()).thenReturn(List.of(s1, s2));

            mockMvc.perform(get("/api/dev/inspect/db/recent").param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    // s2 has later submittedAt → comes first after sort
                    .andExpect(jsonPath("$[0].verdict").value("WRONG_ANSWER"))
                    .andExpect(jsonPath("$[1].verdict").value("ACCEPTED"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should honour limit and not return more entries than requested")
        void testRecentSubmissions_LimitEnforced() throws Exception {
            List<SubmissionEntity> entities = List.of(
                    buildSubmission(UUID.randomUUID(), 1L, 101L, "java",
                            Verdict.ACCEPTED, 100, Instant.parse("2024-01-01T10:00:00Z")),
                    buildSubmission(UUID.randomUUID(), 2L, 102L, "python",
                            Verdict.WRONG_ANSWER, 50, Instant.parse("2024-01-02T10:00:00Z")),
                    buildSubmission(UUID.randomUUID(), 3L, 103L, "cpp",
                            Verdict.PENDING, 0, Instant.parse("2024-01-03T10:00:00Z"))
            );
            when(submissionRepository.findAll()).thenReturn(entities);

            mockMvc.perform(get("/api/dev/inspect/db/recent").param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("Database Submission Found Tests")
    class DatabaseSubmissionFoundTests {

        @Test
        @WithMockUser
        @DisplayName("Should return full submission data when found in database")
        void testDbSubmission_Found_ReturnsAllFields() throws Exception {
            UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

            SubmissionTestResultEntity result = SubmissionTestResultEntity.builder()
                    .id(1L)
                    .testCaseId(10L)
                    .verdict(Verdict.ACCEPTED)
                    .runtimeMs(55L)
                    .memoryBytes(1024L)
                    .actualOutput("42")
                    .expectedOutput("42")
                    .errorMessage(null)
                    .build();

            SubmissionEntity entity = SubmissionEntity.builder()
                    .id(id)
                    .userId(7L)
                    .problemId(99L)
                    .problemName("Two Sum")
                    .language("java")
                    .mode("SUBMIT")
                    .sourceCode("class S{}")
                    .verdict(Verdict.ACCEPTED)
                    .score(100)
                    .totalRuntimeMs(55L)
                    .memoryBytes(1024L)
                    .submittedAt(Instant.parse("2024-06-01T12:00:00Z"))
                    .completedAt(Instant.parse("2024-06-01T12:00:05Z"))
                    .testResults(List.of(result))
                    .build();

            when(submissionRepository.findById(id)).thenReturn(Optional.of(entity));

            mockMvc.perform(get("/api/dev/inspect/db/{executionId}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.found").value(true))
                    .andExpect(jsonPath("$.userId").value(7))
                    .andExpect(jsonPath("$.problemId").value(99))
                    .andExpect(jsonPath("$.problemName").value("Two Sum"))
                    .andExpect(jsonPath("$.language").value("java"))
                    .andExpect(jsonPath("$.verdict").value("ACCEPTED"))
                    .andExpect(jsonPath("$.score").value(100))
                    .andExpect(jsonPath("$.testResultCount").value(1))
                    .andExpect(jsonPath("$.testResults[0].testCaseId").value(10))
                    .andExpect(jsonPath("$.testResults[0].verdict").value("ACCEPTED"))
                    .andExpect(jsonPath("$.testResults[0].actualOutput").value("42"));
        }
    }

    @Nested
    @DisplayName("Redis Rate Limit With Keys Tests")
    class RedisRateLimitWithKeysTests {

        @Test
        @WithMockUser
        @DisplayName("Should return populated entries when rate-limit keys exist in Redis")
        void testRedisRateLimit_WithActualKeys_ReturnsEntries() throws Exception {
            String userId = "user-42";
            String key1 = "ratelimit:user:user-42:1717200000";
            String key2 = "ratelimit:user:user-42:1717200060";

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(redisTemplate.keys("ratelimit:user:user-42*")).thenReturn(Set.of(key1, key2));
            when(valueOps.get(key1)).thenReturn("3");
            when(valueOps.get(key2)).thenReturn("5");
            when(redisTemplate.getExpire(key1, TimeUnit.SECONDS)).thenReturn(30L);
            when(redisTemplate.getExpire(key2, TimeUnit.SECONDS)).thenReturn(90L);

            mockMvc.perform(get("/api/dev/inspect/redis/rate/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId))
                    .andExpect(jsonPath("$.keys").isArray())
                    .andExpect(jsonPath("$.keys.length()").value(2));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return empty keys list when redisTemplate.keys returns null")
        void testRedisRateLimit_NullKeys_ReturnsEmptyList() throws Exception {
            when(redisTemplate.keys(anyString())).thenReturn(null);

            mockMvc.perform(get("/api/dev/inspect/redis/rate/{userId}", "user-null"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.keys").isArray())
                    .andExpect(jsonPath("$.keys.length()").value(0));
        }

        @Test
        @WithMockUser
        @DisplayName("Should handle null value for a rate-limit key gracefully")
        void testRedisRateLimit_NullValueForKey_ReturnsNullString() throws Exception {
            String userId = "user-nullval";
            String key = "ratelimit:user:user-nullval:ts";

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(redisTemplate.keys("ratelimit:user:user-nullval*")).thenReturn(Set.of(key));
            when(valueOps.get(key)).thenReturn(null);
            when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(60L);

            mockMvc.perform(get("/api/dev/inspect/redis/rate/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.keys[0].value").value("null"));
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private SubmissionEntity buildSubmission(UUID id, Long userId, Long problemId,
                                             String language, Verdict verdict,
                                             int score, Instant submittedAt) {
        return SubmissionEntity.builder()
                .id(id)
                .userId(userId)
                .problemId(problemId)
                .problemName("Problem " + problemId)
                .language(language)
                .mode("SUBMIT")
                .sourceCode("// code")
                .verdict(verdict)
                .score(score)
                .totalRuntimeMs(100L)
                .memoryBytes(512L)
                .submittedAt(submittedAt)
                .completedAt(submittedAt.plusSeconds(2))
                .testResults(List.of())
                .build();
    }
}
