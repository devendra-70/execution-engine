package com.epam.execution_engine_service.gateway;

import com.epam.execution_engine_service.persistence.entity.ExecutionRequest;
import com.epam.execution_engine_service.service.ExecutionRegistrationResponse;
import com.epam.execution_engine_service.service.ExecutionRegistrationService;
import com.epam.execution_engine_service.util.IpAddressExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ExecutionController (SRS §3.2)
 * REST endpoints for execution management:
 * - POST /api/executions: Submit code for execution
 * - GET /api/executions/{executionId}/status: Check execution status (REST fallback)
 * 
 * Auth: Bearer JWT required for all endpoints
 * Response: HTTP 202 Accepted for submission, 200 OK for status queries
 */
@Slf4j
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {
    
    private final ExecutionRegistrationService executionRegistrationService;
    private final ExecutionStatusService executionStatusService;
    private final IpAddressExtractor ipAddressExtractor;
    
    /**
     * Submit a code execution request (SRS §3.2)
     * POST /api/executions
     * 
     * @param request ExecutionRequest with problemId, language, mode, sourceCode
     * @param httpServletRequest HTTP request for IP extraction
     * @return HTTP 202 Accepted with ExecutionRegistrationResponse (executionId + status)
     */
    @PostMapping
    public ResponseEntity<ExecutionRegistrationResponse> submitExecution(
            @Valid @RequestBody ExecutionRequest request,
            HttpServletRequest httpServletRequest) {
        
        log.info("Received execution request for problemId: {}, language: {}, mode: {}",
                request.getProblemId(), request.getLanguage(), request.getMode());
        
        String userId = extractUserIdFromContext();
        String clientIp = ipAddressExtractor.extractClientIp(httpServletRequest);
        
        log.debug("Client IP: {}, UserId: {}", clientIp, userId);
        
        // Register execution (two-phase: Redis + Kafka)
        ExecutionRegistrationResponse response = 
                executionRegistrationService.registerExecution(request, userId, clientIp);
        
        log.info("Execution registered successfully: {} for userId: {}", 
                response.getExecutionId(), userId);
        
        // Return HTTP 202 Accepted
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    /**
     * Get execution status (SRS §3.2)
     * GET /api/executions/{executionId}/status
     * 
     * REST fallback for WebSocket connections (e.g., if client loses WS connection)
     * 
     * @param executionId UUID of the execution to check
     * @return HTTP 200 OK with ExecutionStatusResponse or 404 if not found
     */
    @GetMapping("/{executionId}/status")
    public ResponseEntity<ExecutionStatusResponse> getExecutionStatus(
            @PathVariable String executionId) {
        
        log.debug("Fetching execution status for executionId: {}", executionId);
        
        try {
            UUID execId = UUID.fromString(executionId);
            ExecutionStatusResponse status = executionStatusService.getExecutionStatus(execId);
            
            if (status == null) {
                log.debug("Execution status not found: {}", executionId);
                return ResponseEntity.notFound().build();
            }
            
            log.debug("Execution status retrieved: {} -> {}", executionId, status.getStatus());
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid execution ID format: {}", executionId);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Extracts userId from Spring Security context
     * Tries multiple sources: JWT principal, authentication name, request attribute
     * @return userId or "anonymous" if not found
     */
    private String extractUserIdFromContext() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        
        if (securityContext != null && securityContext.getAuthentication() != null) {
            Object principal = securityContext.getAuthentication().getPrincipal();
            if (principal instanceof String) {
                return (String) principal;
            }
            String name = securityContext.getAuthentication().getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        
        log.warn("userId not found in SecurityContext");
        return "anonymous";
    }
}
