package com.epam.execution_engine_service.gateway.rest;

import lombok.RequiredArgsConstructor;
import com.epam.execution_engine_service.gateway.service.ExecutionStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SRP: Handles only HTTP status-query concerns.
 * DIP: Depends on {@link ExecutionStatusService} interface, not Redis directly.
 */
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionStatusController {

    private final ExecutionStatusService executionStatusService;

    @GetMapping("/{executionId}/status")
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable String executionId) {
        return executionStatusService.getStatus(executionId)
                .map(status -> ResponseEntity.ok(Map.of(
                        "executionId", executionId,
                        "status", status)))
                .orElse(ResponseEntity.notFound().build());
    }
}