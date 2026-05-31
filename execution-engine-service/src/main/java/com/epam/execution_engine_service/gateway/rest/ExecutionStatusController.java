package com.epam.execution_engine_service.gateway.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import com.epam.execution_engine_service.gateway.service.ExecutionStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * SRP: Handles only HTTP status-query concerns.
 * DIP: Depends on {@link ExecutionStatusService} interface, not Redis directly.
 *
 * When the execution is COMPLETED the Redis KV holds a JSON summary with
 * verdict / score / totalRuntimeMs so the REST client gets the same data
 * that is pushed via WebSocket — no second DB call needed.
 */
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionStatusController {

    private final ExecutionStatusService executionStatusService;
    private final ObjectMapper objectMapper;

    @GetMapping("/{executionId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String executionId) {
        return executionStatusService.getStatus(executionId)
                .map(value -> {
                    // If the stored value is a JSON object (COMPLETED summary), return it enriched
                    if (value.startsWith("{")) {
                        try {
                            Map<String, Object> data = new HashMap<>(
                                    objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {}));
                            data.put("executionId", executionId);
                            return ResponseEntity.ok(data);
                        } catch (Exception ignored) { /* fall through to plain status */ }
                    }
                    // Plain status string (PENDING / PROCESSING / FAILED)
                    Map<String, Object> plain = new HashMap<>();
                    plain.put("executionId", executionId);
                    plain.put("status", value);
                    return ResponseEntity.<Map<String, Object>>ok(plain);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}