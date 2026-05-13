package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.gateway.exception.ExecutionRegistrationException;
import com.epam.execution_engine_service.persistence.entity.ExecutionRequest;
import com.epam.execution_engine_service.persistence.entity.ExecutionStatus;
import com.epam.execution_engine_service.persistence.entity.ExecutionStatusEnum;
import com.epam.execution_engine_service.persistence.entity.ExecutionTaskEvent;
import com.epam.execution_engine_service.persistence.repository.ExecutionStatusRepository;
import com.epam.execution_engine_service.util.RequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ExecutionRegistrationService
 * Orchestrates the two-phase execution registration process:
 * 1. Validate request
 * 2. Check rate limit
 * 3. Write PENDING status to Redis with TTL
 * 4. Publish ExecutionTaskEvent to Kafka topic "execution-tasks"
 * Returns HTTP 202 Accepted on success
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionRegistrationService {
    
    private final RequestValidator requestValidator;
    private final RateLimiterService rateLimiterService;
    private final ExecutionIdGeneratorService executionIdGeneratorService;
    private final ExecutionStatusRepository executionStatusRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Registers and submits an execution request
     * Two-phase process:
     * Phase 1: Validate → Rate Limit → Redis Write
     * Phase 2: Kafka Publish
     * @param request The execution request
     * @param userId The user ID from JWT
     * @param clientIp The client IP address
     * @return ExecutionResponse with executionId and status
     * @throws com.epam.execution_engine_service.gateway.exception.ValidationException if request validation fails
     * @throws com.epam.execution_engine_service.gateway.exception.RateLimitException if rate limit exceeded
     * @throws ExecutionRegistrationException if registration or publishing fails
     */
    public ExecutionRegistrationResponse registerExecution(
            ExecutionRequest request,
            String userId,
            String clientIp) {
        
        // Phase 1: Validate request (ValidationException propagates naturally)
        requestValidator.validate(request);
        log.debug("Request validation passed");
        
        // Phase 1: Check rate limits (RateLimitException propagates naturally)
        rateLimiterService.checkRateLimit(userId, clientIp);
        log.debug("Rate limit check passed for userId: {}, clientIp: {}", userId, clientIp);
        
        // Phase 1: Generate execution ID
        String executionId = executionIdGeneratorService.generateExecutionId();
        log.debug("Generated execution ID: {}", executionId);
        
        // Phase 1: Write PENDING status to Redis with TTL
        long submittedAtMs = System.currentTimeMillis();
        ExecutionStatus executionStatus = ExecutionStatus.builder()
                .executionId(executionId)
                .status(ExecutionStatusEnum.PENDING)
                .submittedAtMs(submittedAtMs)
                .userId(userId)
                .problemId(request.getProblemId())
                .language(request.getLanguage())
                .mode(request.getMode())
                .build();
        
        try {
            executionStatusRepository.save(executionStatus);
            log.info("ExecutionStatus saved to Redis: {}", executionId);
        } catch (Exception e) {
            log.error("Failed to save execution status to Redis", e);
            throw new ExecutionRegistrationException(
                    "Failed to save execution status to Redis: " + e.getMessage(), e);
        }
        
        // Phase 2: Create and publish ExecutionTaskEvent to Kafka
        ExecutionTaskEvent taskEvent = ExecutionTaskEvent.builder()
                .executionId(executionId)
                .userId(userId)
                .problemId(request.getProblemId())
                .language(request.getLanguage())
                .mode(request.getMode())
                .sourceCode(request.getSourceCode())
                .submittedAtMs(submittedAtMs)
                .build();
        
        try {
            // Publish with userId as partition key for consistency
            // Timeout after 10 seconds to prevent thread pool starvation (SRS Section 7.1)
            kafkaTemplate.send("execution-tasks", userId, taskEvent)
                    .get(10, TimeUnit.SECONDS); // Block with timeout to prevent indefinite blocking
            log.info("ExecutionTaskEvent published to Kafka: {}", executionId);
        } catch (TimeoutException e) {
            log.error("Timeout publishing task event to Kafka after 10 seconds", e);
            throw new ExecutionRegistrationException(
                    "Kafka publish timeout: request took too long", e);
        } catch (Exception e) {
            log.error("Failed to publish task event to Kafka", e);
            throw new ExecutionRegistrationException(
                    "Failed to publish task event to Kafka: " + e.getMessage(), e);
        }
        
        // Return success response
        return ExecutionRegistrationResponse.builder()
                .executionId(executionId)
                .status("PENDING")
                .submittedAt(executionStatus.getSubmittedAtIso8601())
                .build();
    }
}
