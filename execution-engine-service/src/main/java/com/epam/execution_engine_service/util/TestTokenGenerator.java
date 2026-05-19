package com.epam.execution_engine_service.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Run with: mvnw exec:java -pl execution-engine-app -Dexec.mainClass=org.codeval.execution.util.TestTokenGenerator
 */
@Slf4j
public class TestTokenGenerator {
    public static void main(String[] args) {
        String secret = System.getenv().getOrDefault("JWT_SECRET",
                "changeme-very-long-secret-key-for-dev-only-32chars");
        long userId = 1L;

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject("testuser")
                .claims(Map.of("userId", userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000)) // 1 hour
                .signWith(key)
                .compact();

        log.info("=== TEST JWT TOKEN ===");
        log.info("{}", token);
        log.info("");
        log.info("=== USAGE ===");
        log.info("# Health check (no auth):");
        log.info("curl http://localhost:8080/actuator/health");
        log.info("");
        log.info("# Submit execution:");
        log.info("curl -X POST http://localhost:8080/api/executions \\");
        log.info("  -H \"Authorization: Bearer {}\" \\", token);
        log.info("  -H \"Content-Type: application/json\" \\");
        log.info("  -d '{{\"problemId\":1,\"language\":\"java\",\"mode\":\"submit\",\"sourceCode\":\"public class Solution {{ public static void main(String[] args) {{ System.out.println(\\\\\"Hello World\\\\\"); }} }}\"}'");
        log.info("");
        log.info("# Check status (replace <id> with executionId from above):");
        log.info("curl http://localhost:8080/api/executions/<id>/status \\");
        log.info("  -H \"Authorization: Bearer {}\"", token);
        log.info("");
        log.info("# WebSocket: open test-client.html in browser");
    }
}

