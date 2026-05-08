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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ExecutionController
 * REST endpoint for submitting code execution requests
 * Endpoint: POST /api/executions
 * Auth: Bearer JWT required
 * Response: HTTP 202 Accepted with ExecutionRegistrationResponse
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExecutionController {
    
    private final ExecutionRegistrationService executionRegistrationService;
    private final IpAddressExtractor ipAddressExtractor;
    
    /**
     * Submit a code execution request
     * @param request ExecutionRequest with problemId, language, mode, sourceCode
     * @param httpServletRequest HTTP request for IP extraction
     * @return HTTP 202 Accepted with ExecutionRegistrationResponse
     */
    @PostMapping("/executions")
    public ResponseEntity<ExecutionRegistrationResponse> submitExecution(
            @Valid @RequestBody ExecutionRequest request,
            HttpServletRequest httpServletRequest) {
        
        log.info("Received execution request for problemId: {}, language: {}, mode: {}",
                request.getProblemId(), request.getLanguage(), request.getMode());
        
        // Extract userId from JWT (set by JwtAuthenticationFilter)
        SecurityContext securityContext = SecurityContextHolder.getContext();
        String userId = null;
        
        if (securityContext.getAuthentication() != null) {
            Object principal = securityContext.getAuthentication().getPrincipal();
            if (principal instanceof String) {
                userId = (String) principal;
            } else if (principal != null) {
                // Handle case where principal is a UserDetails object (e.g., @WithMockUser)
                userId = securityContext.getAuthentication().getName();
            }
        }
        
        // Fallback to request attribute if not in SecurityContext
        if (userId == null) {
            userId = (String) httpServletRequest.getAttribute("userId");
        }
        
        if (userId == null) {
            log.warn("userId not found in SecurityContext or request attributes");
            userId = "anonymous";
        }
        
        // Extract client IP address
        String clientIp = ipAddressExtractor.extractClientIp(httpServletRequest);
        log.debug("Client IP: {}", clientIp);
        
        // Register execution (two-phase: Redis + Kafka)
        ExecutionRegistrationResponse response = 
                executionRegistrationService.registerExecution(request, userId, clientIp);
        
        log.info("Execution registered successfully: {} for userId: {}", 
                response.getExecutionId(), userId);
        
        // Return HTTP 202 Accepted
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
