package com.epam.execution_engine_service.gateway.filter;

import com.epam.execution_engine_service.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JwtAuthenticationFilter — JWT token validation filter (SRS §3.1)
 * 
 * Validates Bearer JWT tokens in the Authorization header.
 * Populates Spring Security context with authenticated user.
 * Returns 401 Unauthorized if token is invalid or missing.
 * 
 * Allows: /actuator/health, /ws handshake
 * Requires auth: /api/executions, /api/executions/{id}/status
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Skip auth for health check
            if (request.getRequestURI().startsWith("/actuator")) {
                filterChain.doFilter(request, response);
                return;
            }

            // If authentication already exists in SecurityContext (e.g., from @WithMockUser in tests),
            // skip JWT validation and proceed
            if (SecurityContextHolder.getContext().getAuthentication() != null &&
                    SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
                filterChain.doFilter(request, response);
                return;
            }

            // Extract JWT from Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
                return;
            }

            // Extract token (remove "Bearer " prefix)
            String token = authHeader.substring(7);

            // Validate token and get claims
            Optional<Claims> claimsOptional = jwtTokenProvider.validateAndGetClaims(token);
            if (claimsOptional.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token");
                return;
            }

            Claims claims = claimsOptional.get();

            // Extract userId and create authentication
            Long userId = jwtTokenProvider.extractUserId(claims);
            String subject = jwtTokenProvider.extractSubject(claims);

            if (userId == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "UserId not found in JWT");
                return;
            }

            // Create Authentication object
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userId.toString(),  // principal
                    null,               // credentials (not needed)
                    authorities         // authorities
            );

            // Set authentication in Security Context
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Continue the filter chain
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
        }
    }

}
