package org.codeval.execution.gateway.rest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.codeval.execution.gateway.security.JwtProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * DEV-ONLY endpoint — generates a signed JWT for local testing.
 * Only active when the "dev" profile is present (default in docker-compose via SPRING_PROFILES_ACTIVE=dev).
 * Remove or guard this controller before any production deployment.
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Profile("dev")
public class DevTokenController {

    private final JwtProperties jwtProperties;

    @GetMapping("/token")
    public Map<String, String> generateToken(
            @RequestParam(defaultValue = "1") long userId,
            @RequestParam(defaultValue = "testuser") String subject) {

        SecretKey key = Keys.hmacShaKeyFor(
                jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(subject)
                .claims(Map.of("userId", userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000)) // 1 hour
                .signWith(key)
                .compact();

        return Map.of(
                "token", token,
                "userId", String.valueOf(userId),
                "expiresIn", "3600s"
        );
    }
}

