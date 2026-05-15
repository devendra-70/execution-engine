package com.epam.execution_engine_service.gateway.rest;

import lombok.RequiredArgsConstructor;
import com.epam.execution_engine_service.domain.ExecutionStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionStatusController {

    private final StringRedisTemplate redisTemplate;

    @GetMapping("/{executionId}/status")
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable String executionId) {
        String key = "execution:status:" + executionId;
        String status = redisTemplate.opsForValue().get(key);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "executionId", executionId,
                "status", status
        ));
    }
}