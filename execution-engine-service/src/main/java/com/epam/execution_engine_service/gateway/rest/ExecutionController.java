package com.epam.execution_engine_service.gateway.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.domain.ExecutionRequest;
import com.epam.execution_engine_service.domain.ExecutionStatus;
import com.epam.execution_engine_service.gateway.service.ExecutionSubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * SRP: Handles only HTTP concerns — request validation and response shaping.
 * All submission logic is delegated to {@link ExecutionSubmissionService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionSubmissionService submissionService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> submitExecution(
            @Valid @RequestBody ExecutionRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        UUID executionId = submissionService.submit(request, userId);

        return ResponseEntity.accepted().body(Map.of(
                "executionId", executionId.toString(),
                "status", ExecutionStatus.PENDING.name()
        ));
    }
}