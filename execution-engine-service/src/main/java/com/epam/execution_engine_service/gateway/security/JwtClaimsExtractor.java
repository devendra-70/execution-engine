package com.epam.execution_engine_service.gateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

/**
 * JWT Claims Extractor (in security package)
 * Extracts specific claims from JWT Claims object
 */
@Component
public class JwtClaimsExtractor {
    
    /**
     * Extracts userId from JWT claims
     * @param claims The JWT claims
     * @return userId if present, null otherwise
     */
    public String extractUserId(Claims claims) {
        Object subject = claims.get("userId");
        if (subject == null) {
            subject = claims.getSubject();
        }
        return subject != null ? subject.toString() : null;
    }
    
    /**
     * Extracts a custom claim value
     * @param claims The JWT claims
     * @param claimName The name of the claim
     * @return The claim value if present, null otherwise
     */
    public String extractClaim(Claims claims, String claimName) {
        Object claim = claims.get(claimName);
        return claim != null ? claim.toString() : null;
    }
}
