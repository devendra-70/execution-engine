package org.codeval.execution.gateway.rest;

import lombok.RequiredArgsConstructor;
import org.codeval.execution.persistence.entity.SubmissionEntity;
import org.codeval.execution.persistence.entity.SubmissionTestResultEntity;
import org.codeval.execution.persistence.repository.SubmissionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * DEV-ONLY — exposes Redis KV and DB submission data for local testing visibility.
 * Active only on the "dev" Spring profile.
 */
@RestController
@RequestMapping("/api/dev/inspect")
@RequiredArgsConstructor
@Profile("dev")
public class DevInspectController {

    private final StringRedisTemplate redisTemplate;
    private final SubmissionRepository submissionRepository;

    // ------------------------------------------------------------------
    // Redis
    // ------------------------------------------------------------------

    /** GET /api/dev/inspect/redis/{executionId}
     *  Returns the execution:status:{id} key value + TTL from Redis */
    @GetMapping("/redis/{executionId}")
    public ResponseEntity<Map<String, Object>> redisStatus(@PathVariable String executionId) {
        String key = "execution:status:" + executionId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return ResponseEntity.ok(Map.of(
                    "key", key,
                    "exists", false,
                    "value", "—",
                    "ttlSeconds", -2
            ));
        }
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "exists", true,
                "value", value,
                "ttlSeconds", ttl != null ? ttl : -1
        ));
    }

    /** GET /api/dev/inspect/redis/rate/{userId}
     *  Returns the rate-limit key for a userId */
    @GetMapping("/redis/rate/{userId}")
    public ResponseEntity<Map<String, Object>> redisRateLimit(@PathVariable String userId) {
        String pattern = "ratelimit:user:" + userId + "*";
        Set<String> keys = redisTemplate.keys(pattern);
        List<Map<String, Object>> entries = new ArrayList<>();
        if (keys != null) {
            for (String k : keys) {
                String val = redisTemplate.opsForValue().get(k);
                Long ttl = redisTemplate.getExpire(k, TimeUnit.SECONDS);
                entries.add(Map.of(
                        "key", k,
                        "value", val != null ? val : "null",
                        "ttlSeconds", ttl != null ? ttl : -1
                ));
            }
        }
        return ResponseEntity.ok(Map.of("userId", userId, "keys", entries));
    }

    // ------------------------------------------------------------------
    // Database
    // ------------------------------------------------------------------

    /** GET /api/dev/inspect/db/{executionId}
     *  Returns the submission record + test results from PostgreSQL */
    @GetMapping("/db/{executionId}")
    public ResponseEntity<Map<String, Object>> dbSubmission(@PathVariable String executionId) {
        UUID id;
        try {
            id = UUID.fromString(executionId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid UUID: " + executionId));
        }

        Optional<SubmissionEntity> opt = submissionRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "executionId", executionId,
                    "found", false,
                    "message", "No record in DB yet — still processing or failed before persist"
            ));
        }

        SubmissionEntity s = opt.get();
        List<Map<String, Object>> testResults = s.getTestResults().stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("testCaseId", r.getTestCaseId());
                    m.put("verdict", r.getVerdict());
                    m.put("runtimeMs", r.getRuntimeMs());
                    m.put("memoryBytes", r.getMemoryBytes());
                    m.put("actualOutput", r.getActualOutput());
                    m.put("expectedOutput", r.getExpectedOutput());
                    m.put("errorMessage", r.getErrorMessage());
                    return m;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("executionId", s.getId());
        result.put("userId", s.getUserId());
        result.put("problemId", s.getProblemId());
        result.put("problemName", s.getProblemName());
        result.put("language", s.getLanguage());
        result.put("mode", s.getMode());
        result.put("verdict", s.getVerdict());
        result.put("score", s.getScore());
        result.put("totalRuntimeMs", s.getTotalRuntimeMs());
        result.put("memoryBytes", s.getMemoryBytes());
        result.put("submittedAt", s.getSubmittedAt());
        result.put("completedAt", s.getCompletedAt());
        result.put("testResultCount", testResults.size());
        result.put("testResults", testResults);
        return ResponseEntity.ok(result);
    }

    /** GET /api/dev/inspect/db/recent?limit=10
     *  Returns the most recent N submissions */
    @GetMapping("/db/recent")
    public ResponseEntity<List<Map<String, Object>>> recentSubmissions(
            @RequestParam(defaultValue = "10") int limit) {

        List<Map<String, Object>> rows = submissionRepository.findAll().stream()
                .sorted(Comparator.comparing(SubmissionEntity::getSubmittedAt).reversed())
                .limit(limit)
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("executionId", s.getId());
                    m.put("userId", s.getUserId());
                    m.put("problemId", s.getProblemId());
                    m.put("mode", s.getMode());
                    m.put("verdict", s.getVerdict());
                    m.put("score", s.getScore());
                    m.put("totalRuntimeMs", s.getTotalRuntimeMs());
                    m.put("submittedAt", s.getSubmittedAt());
                    m.put("completedAt", s.getCompletedAt());
                    m.put("testResultCount", s.getTestResults().size());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(rows);
    }
}
