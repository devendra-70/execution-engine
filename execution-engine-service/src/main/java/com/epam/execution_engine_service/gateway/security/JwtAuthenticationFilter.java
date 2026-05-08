package com.epam.execution_engine_service.gateway.security;

import com.epam.execution_engine_service.gateway.exception.AuthenticationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Authentication Filter (SRS Section 3.3)
 * Extracts JWT token from Authorization header and validates it
 * Stores userId in SecurityContext for downstream processing
 * Returns HTTP 401 on missing/invalid tokens for protected endpoints
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtClaimsExtractor jwtClaimsExtractor;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        boolean isProtectedEndpoint = requestPath.startsWith("/api/");
        
        try {
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                try {
                    // Validate token and extract claims (SRS Section 3.3: HMAC-SHA256 validation)
                    Claims claims = jwtTokenProvider.validateAndExtractClaims(token);
                    
                    // Extract userId from claims
                    String userId = jwtClaimsExtractor.extractUserId(claims);
                    
                    if (userId != null) {
                        // Set userId in SecurityContext (SRS-compliant)
                        UsernamePasswordAuthenticationToken authentication = 
                                new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        
                        // Store userId in request attribute for controller access
                        request.setAttribute("userId", userId);
                    } else {
                        if (isProtectedEndpoint) {
                            sendUnauthorizedResponse(response, "User ID not found in JWT claims");
                            return;
                        }
                    }
                } catch (Exception e) {
                    // Invalid token signature or parsing failed
                    if (isProtectedEndpoint) {
                        logger.warn("JWT validation failed: " + e.getMessage());
                        sendUnauthorizedResponse(response, "Invalid JWT token");
                        return;
                    }
                    // For non-protected endpoints, continue without authentication
                }
            } else if (isProtectedEndpoint) {
                // Missing Bearer token on protected endpoint (SRS Section 3.3)
                sendUnauthorizedResponse(response, "Missing Authorization header with Bearer token");
                return;
            }
            
        } catch (Exception e) {
            // Unexpected error
            logger.error("Unexpected error in JWT filter", e);
            if (isProtectedEndpoint) {
                sendUnauthorizedResponse(response, "Authentication processing failed");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Send HTTP 401 Unauthorized response with JSON error body
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 401);
        errorResponse.put("error", "Authentication Error");
        errorResponse.put("message", message);
        errorResponse.put("timestamp", LocalDateTime.now());
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
