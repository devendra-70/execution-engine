package org.codeval.execution.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Run with: mvnw exec:java -pl execution-engine-app -Dexec.mainClass=org.codeval.execution.util.TestTokenGenerator
 */
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

        System.out.println("=== TEST JWT TOKEN ===");
        System.out.println(token);
        System.out.println();
        System.out.println("=== USAGE ===");
        System.out.println("# Health check (no auth):");
        System.out.println("curl http://localhost:8080/actuator/health");
        System.out.println();
        System.out.println("# Submit execution:");
        System.out.println("curl -X POST http://localhost:8080/api/executions \\");
        System.out.println("  -H \"Authorization: Bearer " + token + "\" \\");
        System.out.println("  -H \"Content-Type: application/json\" \\");
        System.out.println("  -d '{\"problemId\":1,\"language\":\"java\",\"mode\":\"submit\",\"sourceCode\":\"public class Solution { public static void main(String[] args) { System.out.println(\\\\\"Hello World\\\\\"); } }\"}'");
        System.out.println();
        System.out.println("# Check status (replace <id> with executionId from above):");
        System.out.println("curl http://localhost:8080/api/executions/<id>/status \\");
        System.out.println("  -H \"Authorization: Bearer " + token + "\"");
        System.out.println();
        System.out.println("# WebSocket: open test-client.html in browser");
    }
}

