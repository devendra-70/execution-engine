package com.epam.execution_engine_service.gateway.rest.dev;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.epam.execution_engine_service.gateway.security.JwtProperties;
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
@Tag(name = "Dev Tools (dev profile only)", description = "Local-dev helpers — NOT available in production")
public class DevTokenController {

    private final JwtProperties jwtProperties;

    @Operation(
        summary     = "Generate a dev JWT token",
        description = "**Step 1 of 2** — call this endpoint to get a signed JWT, then click the " +
                      "'Authorize' button at the top of Swagger UI and paste the token. " +
                      "After that you can test secured endpoints like `POST /api/executions`.",
        responses   = {
            @ApiResponse(
                responseCode = "200",
                description  = "JWT token generated",
                content      = @Content(
                    examples = @ExampleObject(
                        name  = "Token response",
                        value = "{\"token\": \"eyJ...\", \"userId\": \"1\", \"expiresIn\": \"3600s\"}"
                    )
                )
            )
        }
    )
    @GetMapping("/token")
    public Map<String, String> generateToken(
            @Parameter(description = "User ID to embed in the JWT", example = "1")
            @RequestParam(defaultValue = "1") long userId,
            @Parameter(description = "Subject claim value", example = "testuser")
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
