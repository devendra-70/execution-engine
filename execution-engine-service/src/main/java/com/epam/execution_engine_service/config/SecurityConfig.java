package com.epam.execution_engine_service.config;

import com.epam.execution_engine_service.gateway.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for JWT-based authentication (SRS §3.6).
 * 
 * Uses custom JwtAuthenticationFilter for simple shared-secret HMAC-SHA256 validation.
 * - Public endpoints: /actuator/health, /ws/** (health checks and WebSocket handshake)
 * - Protected endpoints: /api/executions/** (requires Bearer JWT)
 * - Method-level authorization via @PreAuthorize for fine-grained control
 * - Stateless session management for REST API
 *
 * <p>JWT validation is performed by custom filter before reaching controller code.
 * Invalid or missing tokens result in 401 Unauthorized.
 *
 * @author Execution Engine Team
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Slf4j
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configures the HTTP security filter chain (SRS §3.6).
     *
     * <p>Setup:
     * <ul>
     *   <li>CSRF disabled (stateless REST API)</li>
     *   <li>Stateless session management (no session cookies)</li>
     *   <li>Public endpoints: /actuator/health, /ws/** (WebSocket)</li>
     *   <li>Protected endpoints: /api/executions/** (requires authentication)</li>
     *   <li>Custom JWT filter added before UsernamePasswordAuthenticationFilter</li>
     * </ul>
     *
     * @param http the HttpSecurity builder
     * @return configured SecurityFilterChain
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring HTTP security filter chain for JWT authentication");

        http
                // Disable CSRF for stateless REST API
                .csrf(csrf -> csrf.disable())

                // Use stateless session management (no cookies)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules (SRS Section 3.6)
                .authorizeHttpRequests(authz -> authz
                        // Health check endpoint (no auth required)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // WebSocket endpoint (permitted; JWT validation happens at protocol upgrade)
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        // All /api/executions endpoints require authentication
                        .requestMatchers("/api/executions", "/api/executions/**").authenticated()
                        // Explicitly deny all other routes
                        .anyRequest().denyAll()
                )

                // Add custom JWT filter before authentication filters
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
