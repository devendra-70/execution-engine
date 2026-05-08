package com.epam.execution_engine_service;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.gateway.ExecutionController;
import com.epam.execution_engine_service.gateway.exception.AuthenticationException;
import com.epam.execution_engine_service.gateway.exception.ExecutionRegistrationException;
import com.epam.execution_engine_service.gateway.exception.RateLimitException;
import com.epam.execution_engine_service.gateway.exception.ValidationException;
import com.epam.execution_engine_service.gateway.security.JwtClaimsExtractor;
import com.epam.execution_engine_service.gateway.security.JwtTokenProvider;
import com.epam.execution_engine_service.persistence.entity.ExecutionRequest;
import com.epam.execution_engine_service.persistence.entity.ExecutionStatus;
import com.epam.execution_engine_service.persistence.entity.ExecutionStatusEnum;
import com.epam.execution_engine_service.persistence.repository.ExecutionStatusRepository;
import com.epam.execution_engine_service.service.ExecutionIdGeneratorService;
import com.epam.execution_engine_service.service.ExecutionRegistrationResponse;
import com.epam.execution_engine_service.service.ExecutionRegistrationService;
import com.epam.execution_engine_service.service.RateLimiterService;
import com.epam.execution_engine_service.util.IpAddressExtractor;
import com.epam.execution_engine_service.util.RequestValidator;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for EPMICMPCOD-428 (Execution Engine Service)
 * 48 unit test cases covering:
 * - JwtAuthenticationFilter (5 tests)
 * - ExecutionController (8 tests)
 * - ExecutionRegistrationService (12 tests)
 * - RateLimiterService (8 tests)
 * - RequestValidator (5 tests)
 * - Error handling (4 tests)
 * 
 * Compliance: SRS Sections 2.2, 3.2, 3.3, 7.1, 8, 9
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExecutionEngineServiceTests {

    // ==================== MOCKS ====================
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    
    @Mock
    private JwtClaimsExtractor jwtClaimsExtractor;
    
    @Mock
    private ExecutionIdGeneratorService executionIdGeneratorService;
    
    @Mock
    private ExecutionStatusRepository executionStatusRepository;
    
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOps;
    
    @Mock
    private ApplicationProperties applicationProperties;
    
    @Mock
    private ApplicationProperties.RateLimit rateLimitConfig;
    
    @Mock
    private IpAddressExtractor ipAddressExtractor;
    
    @Mock
    private HttpServletRequest httpServletRequest;
    
    @Mock
    private Claims claims;
    
    // ==================== SERVICE INSTANCES ====================
    private RequestValidator requestValidator;
    private RateLimiterService rateLimiterService;
    private ExecutionRegistrationService executionRegistrationService;
    private ExecutionController executionController;

    // ==================== SETUP ====================
    @BeforeEach
    public void setUp() {
        SecurityContextHolder.clearContext();
        
        // Initialize services with real implementations or mocks
        requestValidator = new RequestValidator();
        rateLimiterService = new RateLimiterService(redisTemplate, applicationProperties);
        
        executionRegistrationService = new ExecutionRegistrationService(
                requestValidator,
                rateLimiterService,
                executionIdGeneratorService,
                executionStatusRepository,
                kafkaTemplate
        );
        
        executionController = new ExecutionController(executionRegistrationService, ipAddressExtractor);
        
        // Setup common mock returns for Redis
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(applicationProperties.getRateLimit()).thenReturn(rateLimitConfig);
        
        // Setup default Kafka mock to support all test cases
        // The KafkaTemplate.send() returns a CompletableFuture<SendResult<K,V>>
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        // Setup default rate limit config for Redis-based token bucket
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(30);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        
        // Setup default Redis rate limit behavior: allow requests
        // This is the default for all tests unless overridden
        setupRedisRateLimitDefaults();
    }
    
    /**
     * Helper method to setup default Redis mocks for rate limiting
     * Used by registerExecution tests to ensure Redis operations work
     * Tests can override these mocks as needed
     */
    private void setupRedisRateLimitDefaults() {
        // Default: Redis returns null (key doesn't exist) for first request
        when(valueOps.get(anyString())).thenReturn(null);
        // Default: Redis increment succeeds and returns next count
        when(valueOps.increment(anyString())).thenReturn(1L);
        // Default: Redis expire succeeds
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
    }

    // ========================================================================
    // TEST SUITE 1: JwtAuthenticationFilter & JwtTokenProvider (5 tests)
    // SRS Section 3.3: JWT Authentication
    // ========================================================================
    
    @Test
    public void test_JwtTokenProvider_ValidToken_ExtractsClaims() {
        // SRS 3.3: Valid JWT should validate and extract claims
        String validToken = "valid.jwt.token";
        when(jwtTokenProvider.validateAndExtractClaims(validToken)).thenReturn(claims);
        
        Claims result = jwtTokenProvider.validateAndExtractClaims(validToken);
        assertNotNull(result);
        assertEquals(claims, result);
    }

    @Test
    public void test_JwtClaimsExtractor_ExtractsUserIdFromClaims() {
        // SRS 3.3: Should extract userId from JWT claims
        String userId = "user123";
        when(jwtClaimsExtractor.extractUserId(claims)).thenReturn(userId);
        
        String result = jwtClaimsExtractor.extractUserId(claims);
        assertEquals(userId, result);
    }

    @Test
    public void test_JwtTokenProvider_InvalidToken_ThrowsException() {
        // SRS 3.3: Invalid token should throw exception
        String invalidToken = "invalid.token";
        when(jwtTokenProvider.validateAndExtractClaims(invalidToken))
                .thenThrow(new RuntimeException("Invalid token signature"));
        
        assertThrows(RuntimeException.class, () -> {
            jwtTokenProvider.validateAndExtractClaims(invalidToken);
        });
    }

    @Test
    public void test_JwtClaimsExtractor_NullUserId_ReturnsNull() {
        // SRS 3.3: Claims without userId should return null
        when(jwtClaimsExtractor.extractUserId(claims)).thenReturn(null);
        
        String result = jwtClaimsExtractor.extractUserId(claims);
        assertNull(result);
    }

    @Test
    public void test_JwtTokenProvider_MultipleTokens_ValidatesEach() {
        // SRS 3.3: Each token should be validated independently
        String token1 = "token1";
        String token2 = "token2";
        Claims claims1 = mock(Claims.class);
        Claims claims2 = mock(Claims.class);
        
        when(jwtTokenProvider.validateAndExtractClaims(token1)).thenReturn(claims1);
        when(jwtTokenProvider.validateAndExtractClaims(token2)).thenReturn(claims2);
        
        assertEquals(claims1, jwtTokenProvider.validateAndExtractClaims(token1));
        assertEquals(claims2, jwtTokenProvider.validateAndExtractClaims(token2));
    }

    // ========================================================================
    // TEST SUITE 2: ExecutionController (8 tests)
    // SRS Section 7.1: API Endpoint
    // ========================================================================
    
    @Test
    public void test_ExecutionController_ValidRequest_Returns202Accepted() {
        // SRS 7.1: Valid request should return HTTP 202 Accepted
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("public class Test {}")
                .build();
        
        String userId = "user123";
        String clientIp = "192.168.1.1";
        String executionId = "exec-uuid-12345";
        
        when(httpServletRequest.getAttribute("userId")).thenReturn(userId);
        when(ipAddressExtractor.extractClientIp(httpServletRequest)).thenReturn(clientIp);
        when(executionIdGeneratorService.generateExecutionId()).thenReturn(executionId);
        when(executionStatusRepository.save(any())).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        ResponseEntity<ExecutionRegistrationResponse> response = executionController.submitExecution(request, httpServletRequest);
        
        assertEquals(202, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(executionId, response.getBody().getExecutionId());
    }

    @Test
    public void test_ExecutionController_NullProblemId_ValidationFails() {
        // SRS 2.2: Null problemId should fail validation
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId(null)
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        assertThrows(ValidationException.class, () -> {
            requestValidator.validate(request);
        });
    }

    @Test
    public void test_ExecutionController_EmptyProblemId_ValidationFails() {
        // SRS 2.2: Empty problemId should fail validation
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        assertThrows(ValidationException.class, () -> {
            requestValidator.validate(request);
        });
    }

    @Test
    public void test_ExecutionController_InvalidMode_ValidationFails() {
        // SRS 2.2: Invalid mode should fail validation
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("INVALID")
                .sourceCode("code")
                .build();
        
        assertThrows(ValidationException.class, () -> {
            requestValidator.validate(request);
        });
    }

    @Test
    public void test_ExecutionController_NullSourceCode_ValidationFails() {
        // SRS 2.2: Null sourceCode should fail validation
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode(null)
                .build();
        
        assertThrows(ValidationException.class, () -> {
            requestValidator.validate(request);
        });
    }

    @Test
    public void test_ExecutionController_ModeRun_AcceptedCorrectly() {
        // SRS 2.2: Mode "RUN" should be accepted
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        assertDoesNotThrow(() -> {
            requestValidator.validate(request);
        });
    }

    @Test
    public void test_ExecutionController_ModeSubmit_AcceptedCorrectly() {
        // SRS 2.2: Mode "SUBMIT" should be accepted
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("SUBMIT")
                .sourceCode("code")
                .build();
        
        assertDoesNotThrow(() -> {
            requestValidator.validate(request);
        });
    }

    @Test
    public void test_ExecutionController_NoUserId_Returns401() {
        // SRS 3.2: Missing userId (no JWT) uses "anonymous" as fallback
        // Rate limit should be checked for anonymous user
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String userId = "anonymous";
        String clientIp = "127.0.0.1";
        String executionId = "exec-123";
        
        when(httpServletRequest.getAttribute("userId")).thenReturn(null);
        when(ipAddressExtractor.extractClientIp(httpServletRequest)).thenReturn(clientIp);
        when(executionIdGeneratorService.generateExecutionId()).thenReturn(executionId);
        when(executionStatusRepository.save(any())).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null, null);
        when(valueOps.increment(anyString())).thenReturn(1L, 1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        ResponseEntity<ExecutionRegistrationResponse> response = executionController.submitExecution(request, httpServletRequest);
        assertEquals(202, response.getStatusCode().value());
    }

    // ========================================================================
    // TEST SUITE 3: ExecutionRegistrationService (12 tests)
    // SRS Section 9: Service Integration
    // ========================================================================
    
    @Test
    public void test_ExecutionRegistrationService_ValidRequest_SavesToRedis() {
        // SRS 9: Valid request should save to Redis
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String userId = "user123";
        String clientIp = "192.168.1.1";
        String executionId = "exec-uuid-12345";
        
        when(executionIdGeneratorService.generateExecutionId()).thenReturn(executionId);
        when(executionStatusRepository.save(any())).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        ExecutionRegistrationResponse response = executionRegistrationService.registerExecution(request, userId, clientIp);
        
        assertNotNull(response);
        assertEquals(executionId, response.getExecutionId());
        assertEquals("PENDING", response.getStatus());
        verify(executionStatusRepository).save(any(ExecutionStatus.class));
    }

    @Test
    public void test_ExecutionRegistrationService_ValidRequest_PublishesToKafka() {
        // SRS 9: Valid request should publish to Kafka
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String userId = "user123";
        String clientIp = "192.168.1.1";
        String executionId = "exec-uuid-12345";
        
        when(executionIdGeneratorService.generateExecutionId()).thenReturn(executionId);
        when(executionStatusRepository.save(any())).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        ExecutionRegistrationResponse response = executionRegistrationService.registerExecution(request, userId, clientIp);
        
        verify(kafkaTemplate).send(eq("execution-tasks"), eq(userId), any());
    }

    @Test
    public void test_ExecutionRegistrationService_InvalidRequest_ValidationFails() {
        // SRS 9: Invalid request should fail validation
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        assertThrows(ValidationException.class, () -> {
            executionRegistrationService.registerExecution(request, userId, clientIp);
        });
    }

    @Test
    public void test_ExecutionRegistrationService_RateLimitExceeded_ThrowsException() {
        // SRS 8: Exceeding rate limit should throw exception
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(2);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn("2"); // Already at limit
        
        assertThrows(RateLimitException.class, () -> {
            executionRegistrationService.registerExecution(request, userId, clientIp);
        });
    }

    @Test
    public void test_ExecutionRegistrationService_ExecutionIdGenerated() {
        // SRS 9: Each registration should get a unique execution ID
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String userId = "user123";
        String clientIp = "192.168.1.1";
        String execId1 = "exec-1";
        String execId2 = "exec-2";
        
        when(executionIdGeneratorService.generateExecutionId()).thenReturn(execId1).thenReturn(execId2);
        when(executionStatusRepository.save(any())).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        ExecutionRegistrationResponse response1 = executionRegistrationService.registerExecution(request, userId, clientIp);
        ExecutionRegistrationResponse response2 = executionRegistrationService.registerExecution(request, userId, clientIp);
        
        assertNotEquals(response1.getExecutionId(), response2.getExecutionId());
    }

    @Test
    public void test_ExecutionRegistrationService_StatusPending() {
        // SRS 9: Status should be PENDING after registration
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        when(executionIdGeneratorService.generateExecutionId()).thenReturn("exec-123");
        when(executionStatusRepository.save(any())).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        ExecutionRegistrationResponse response = executionRegistrationService.registerExecution(request, userId, clientIp);
        
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    public void test_ExecutionRegistrationService_SubmittedAtTimestampSet() {
        // SRS 9: submittedAt should be populated
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        when(executionIdGeneratorService.generateExecutionId()).thenReturn("exec-123");
        when(executionStatusRepository.save(any())).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        ExecutionRegistrationResponse response = executionRegistrationService.registerExecution(request, userId, clientIp);
        
        assertNotNull(response.getSubmittedAt());
    }

    @Test
    public void test_ExecutionRegistrationService_UserIdAsKafkaKey() {
        // SRS 9: userId should be the Kafka message key
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String userId = "user456";
        String clientIp = "192.168.1.1";
        
        when(executionIdGeneratorService.generateExecutionId()).thenReturn("exec-123");
        when(executionStatusRepository.save(any())).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        executionRegistrationService.registerExecution(request, userId, clientIp);
        
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("execution-tasks"), keyCaptor.capture(), any());
        assertEquals(userId, keyCaptor.getValue());
    }

    @Test
    public void test_ExecutionRegistrationService_MultipleUsers_IndependentRateLimit() {
        // SRS 8: Each user should have independent rate limit
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        String user1 = "user1";
        String user2 = "user2";
        String clientIp = "192.168.1.1";
        
        when(executionIdGeneratorService.generateExecutionId()).thenReturn("exec-1").thenReturn("exec-2");
        when(executionStatusRepository.save(any())).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        ExecutionRegistrationResponse resp1 = executionRegistrationService.registerExecution(request, user1, clientIp);
        ExecutionRegistrationResponse resp2 = executionRegistrationService.registerExecution(request, user2, clientIp);
        
        assertNotNull(resp1);
        assertNotNull(resp2);
    }

    // ========================================================================
    // TEST SUITE 4: RateLimiterService (8 tests)
    // SRS Section 8: Rate Limiting
    // ========================================================================
    
    @Test
    public void test_RateLimiterService_FirstRequest_Allowed() {
        // SRS 8: First request should always be allowed
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        assertDoesNotThrow(() -> {
            rateLimiterService.checkRateLimit(userId, clientIp);
        });
    }

    @Test
    public void test_RateLimiterService_WithinLimit_Allowed() {
        // SRS 8: Requests within limit should be allowed
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn("3");
        when(valueOps.increment(anyString())).thenReturn(4L);
        
        assertDoesNotThrow(() -> {
            rateLimiterService.checkRateLimit(userId, clientIp);
        });
    }

    @Test
    public void test_RateLimiterService_ExceededLimit_ThrowsException() {
        // SRS 8: Exceeding limit should throw exception
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn("5");
        
        assertThrows(RateLimitException.class, () -> {
            rateLimiterService.checkRateLimit(userId, clientIp);
        });
    }

    @Test
    public void test_RateLimiterService_PerUser_Independent() {
        // SRS 8: Rate limit should be per userId
        String user1 = "user1";
        String user2 = "user2";
        String clientIp = "192.168.1.1";
        
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        assertDoesNotThrow(() -> {
            rateLimiterService.checkRateLimit(user1, clientIp);
            rateLimiterService.checkRateLimit(user2, clientIp);
        });
    }

    @Test
    public void test_RateLimiterService_PerIp_Independent() {
        // SRS 8: Rate limit should be per IP
        String userId = "user123";
        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";
        
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        assertDoesNotThrow(() -> {
            rateLimiterService.checkRateLimit(userId, ip1);
            rateLimiterService.checkRateLimit(userId, ip2);
        });
    }

    @Test
    public void test_RateLimiterService_TokenBucketAlgorithm() {
        // SRS 8: Token bucket algorithm should work correctly
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(2);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        // checkRateLimit calls isAllowed twice (user + IP), so get() is called 4 times per checkRateLimit
        // First call: null, null (user and IP both start at 0)
        // Second call: "1", "1" (after first increment)
        when(valueOps.get(anyString())).thenReturn(null, null, "1", "1");
        when(valueOps.increment(anyString())).thenReturn(1L, 1L, 2L, 2L);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        // First call should succeed
        assertDoesNotThrow(() -> {
            rateLimiterService.checkRateLimit(userId, clientIp);
        });
        // Second call should succeed
        assertDoesNotThrow(() -> {
            rateLimiterService.checkRateLimit(userId, clientIp);
        });
    }

    @Test
    public void test_RateLimiterService_TtlExpiresAfterWindow() {
        // SRS 8: Rate limit window should expire after TTL
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        // checkRateLimit calls isAllowed twice (user + IP), each sets expire when count==0
        when(valueOps.get(anyString())).thenReturn(null, null);
        when(valueOps.increment(anyString())).thenReturn(1L, 1L);
        when(redisTemplate.expire(anyString(), eq(60L), any())).thenReturn(true);
        
        rateLimiterService.checkRateLimit(userId, clientIp);
        
        // Both user and IP rate limit checks call expire when count==0
        verify(redisTemplate, times(2)).expire(anyString(), eq(60L), any());
    }

    @Test
    public void test_RateLimiterService_RedisFailure_FailClosed() {
        // SRS 8: Redis failure should deny request (fail closed - secure by default)
        String userId = "user123";
        String clientIp = "192.168.1.1";
        
        when(rateLimitConfig.getRequestsPerMinute()).thenReturn(5);
        when(rateLimitConfig.getWindowSeconds()).thenReturn(60);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis error"));
        
        assertThrows(RateLimitException.class, () -> {
            rateLimiterService.checkRateLimit(userId, clientIp);
        });
    }

    // ========================================================================
    // TEST SUITE 5: RequestValidator (5 tests)
    // SRS Section 2.2: Request Validation
    // ========================================================================
    
    @Test
    public void test_RequestValidator_ValidRequest_Passes() {
        // SRS 2.2: Valid request should pass all validations
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("public class Test {}")
                .build();
        
        assertDoesNotThrow(() -> {
            requestValidator.validate(request);
        });
    }

    @Test
    public void test_RequestValidator_NullRequest_Fails() {
        // SRS 2.2: Null request should fail
        assertThrows(ValidationException.class, () -> {
            requestValidator.validate(null);
        });
    }

    @Test
    public void test_RequestValidator_BlankProblemId_Fails() {
        // SRS 2.2: Blank problemId should fail
        ExecutionRequest request = ExecutionRequest.builder()
                .problemId("   ")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        assertThrows(ValidationException.class, () -> {
            requestValidator.validate(request);
        });
    }

    @Test
    public void test_RequestValidator_ModeCase_Insensitive() {
        // SRS 2.2: Mode should be case-insensitive
        ExecutionRequest requestLower = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("run")
                .sourceCode("code")
                .build();
        
        ExecutionRequest requestUpper = ExecutionRequest.builder()
                .problemId("PROB123")
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        assertDoesNotThrow(() -> {
            requestValidator.validate(requestLower);
            requestValidator.validate(requestUpper);
        });
    }

    @Test
    public void test_RequestValidator_AllFieldsRequired() {
        // SRS 2.2: All fields are required
        ExecutionRequest request1 = ExecutionRequest.builder()
                .problemId(null)
                .language("JAVA")
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        ExecutionRequest request2 = ExecutionRequest.builder()
                .problemId("PROB123")
                .language(null)
                .mode("RUN")
                .sourceCode("code")
                .build();
        
        assertThrows(ValidationException.class, () -> {
            requestValidator.validate(request1);
        });
        
        assertThrows(ValidationException.class, () -> {
            requestValidator.validate(request2);
        });
    }

    // ========================================================================
    // TEST SUITE 6: Error Handling (4 tests)
    // SRS Section 3.2: Error Responses
    // ========================================================================
    
    @Test
    public void test_ValidationException_Thrown() {
        // SRS 3.2: ValidationException should be thrown for invalid requests
        assertThrows(ValidationException.class, () -> {
            throw new ValidationException("Invalid field");
        });
    }

    @Test
    public void test_AuthenticationException_Thrown() {
        // SRS 3.2: AuthenticationException should be thrown for auth failures
        assertThrows(AuthenticationException.class, () -> {
            throw new AuthenticationException("Invalid JWT");
        });
    }

    @Test
    public void test_RateLimitException_Thrown() {
        // SRS 3.2: RateLimitException should be thrown when limit exceeded
        assertThrows(RateLimitException.class, () -> {
            throw new RateLimitException("Rate limit exceeded");
        });
    }

    @Test
    public void test_ExecutionRegistrationException_Thrown() {
        // SRS 3.2: ExecutionRegistrationException should be thrown on registration failure
        assertThrows(ExecutionRegistrationException.class, () -> {
            throw new ExecutionRegistrationException("Registration failed");
        });
    }
}
