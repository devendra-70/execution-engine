package com.epam.execution_engine_service.controller;

import com.epam.execution_engine_service.dto.ExecutionRequest;
import com.epam.execution_engine_service.dto.ExecutionSubmissionResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for code execution submission endpoints.
 *
 * <p>Handles POST /api/executions requests with validation and authentication.
 * All validation failures and authentication errors are handled by centralized
 * exception handlers and return appropriate HTTP status codes without any side effects.
 *
 * @author Execution Engine Team
 */
@RestController
@RequestMapping("/api/executions")
@Validated
@Slf4j
public class ExecutionController {

    /**
     * Submit code for execution (RUN or SUBMIT mode).
     *
     * <p>Accepts a code submission request, validates all required fields,
     * and confirms authentication via JWT bearer token. If validation passes,
     * returns 202 Accepted with an execution ID.
     *
     * <p><strong>Validation:</strong>
     * <ul>
     *   <li>problemId: required, non-blank</li>
     *   <li>language: required, non-blank</li>
     *   <li>mode: required, non-blank, must be RUN or SUBMIT</li>
     *   <li>sourceCode: required, non-blank, max 100KB</li>
     * </ul>
     *
     * <p><strong>Authentication:</strong> Requires Bearer JWT token.
     * Unauthenticated requests return 401 Unauthorized.
     *
     * <p><strong>Side Effects on Success:</strong>
     * Only on successful validation and authentication:
     * <ul>
     *   <li>Generate UUID for execution ID</li>
     *   <li>Write PENDING status to Redis (next stage)</li>
     *   <li>Publish ExecutionTaskEvent to Kafka (next stage)</li>
     * </ul>
     *
     * <p><strong>Side Effects on Failure:</strong>
     * Validation or authentication failures produce NO side effects.
     * No Redis writes, no Kafka publishes.
     *
     * @param request the validated execution request DTO
     * @param authentication the authenticated user (injected by Spring Security)
     * @return ResponseEntity with 202 Accepted and execution submission response
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExecutionSubmissionResponse> submitExecution(
            @RequestBody @Valid ExecutionRequest request,
            Authentication authentication) {

        String userId = authentication.getName();
        UUID executionId = UUID.randomUUID();
        Instant submittedAt = Instant.now();

        log.info("Execution submission received - executionId: {}, userId: {}, problemId: {}, mode: {}",
                executionId, userId, request.getProblemId(), request.getMode());

        // At this point:
        // ✓ User is authenticated (JWT valid)
        // ✓ Request payload is valid (all fields present and conform to constraints)
        // ✓ Mode is valid (RUN or SUBMIT)

        // Downstream processing will be implemented in next stages:
        // 1. Write PENDING status to Redis with TTL
        // 2. Publish ExecutionTaskEvent to Kafka topic execution-tasks

        ExecutionSubmissionResponse response = new ExecutionSubmissionResponse(
                executionId,
                "PENDING",
                submittedAt
        );

        log.debug("Returning 202 Accepted for executionId: {}", executionId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get execution status (REST fallback).
     *
     * <p>Retrieve the current status of a previously submitted execution.
     * This is a fallback for clients that lose the WebSocket connection.
     *
     * <p>Status is retrieved from Redis cache (TTL: 10 minutes).
     * If status is not found in cache, execution may have completed and
     * been written to PostgreSQL.
     *
     * @param executionId the unique execution identifier
     * @param authentication the authenticated user
     * @return ResponseEntity with execution status
     */
    @GetMapping("/{executionId}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> getExecutionStatus(
            @PathVariable UUID executionId,
            Authentication authentication) {

        log.info("Status query for executionId: {}, userId: {}", executionId, authentication.getName());

        // Implementation deferred to next stage
        // Will fetch status from Redis or PostgreSQL
        return ResponseEntity.ok().build();
    }
}
