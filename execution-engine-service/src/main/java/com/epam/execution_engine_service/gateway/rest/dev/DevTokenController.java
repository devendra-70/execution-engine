package com.epam.execution_engine_service.gateway.rest.dev;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import com.epam.execution_engine_service.gateway.security.JwtKeyProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Map;

/**
 * DEV-ONLY endpoint — generates a signed JWT for local testing.
 * DRY: Uses shared {@link JwtKeyProvider} — no duplicate key construction.
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Profile("dev")
public class DevTokenController {

    private final JwtKeyProvider jwtKeyProvider;

    @GetMapping("/token")
    public Map<String, String> generateToken(
            @RequestParam(defaultValue = "1") long userId,
            @RequestParam(defaultValue = "testuser") String subject) {

        String token = Jwts.builder()
                .subject(subject)
                .claims(Map.of("userId", userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(jwtKeyProvider.getKey())
                .compact();

        return Map.of(
                "token", token,
                "userId", String.valueOf(userId),
                "expiresIn", "3600s"
        );
    }
}
