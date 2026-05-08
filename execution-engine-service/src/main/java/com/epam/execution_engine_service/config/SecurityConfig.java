package com.epam.execution_engine_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for OAuth2 JWT-based authentication.
 * 
 * Implements SRS Section 3.6 Security Configuration:
 * - OAuth2 Resource Server with JWT validation
 * - Public endpoints: /actuator/health, /ws/** (health checks and WebSocket handshake)
 * - Protected endpoints: /api/executions/** (requires Bearer JWT)
 * - Method-level authorization via @PreAuthorize for fine-grained control
 * - Stateless session management for REST API
 *
 * <p>JWT validation is performed by Spring Security filter chain.
 * Invalid or missing tokens result in 401 Unauthorized before any controller code executes.
 *
 * @author Execution Engine Team
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Slf4j
public class SecurityConfig {

    /**
     * Configures the HTTP security filter chain.
     *
     * <p>Setup (per SRS Section 3.6):
     * <ul>
     *   <li>CSRF disabled (stateless REST API)</li>
     *   <li>Stateless session management (no session cookies)</li>
     *   <li>Public endpoints: /actuator/health, /ws/** (WebSocket)</li>
     *   <li>Protected endpoints: /api/executions/** (requires authentication)</li>
     *   <li>OAuth2 Resource Server with JWT validation</li>
     * </ul>
     *
     * @param http the HttpSecurity builder
     * @return configured SecurityFilterChain
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring HTTP security filter chain for OAuth2 JWT");

        http
                // Disable CSRF for stateless REST API
                .csrf().disable()

                // Use stateless session management (no cookies)
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()

                // Authorization rules (SRS Section 3.6)
                .authorizeHttpRequests()
                // Health check endpoint (no auth required)
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // WebSocket endpoint (permitted; JWT validation happens at protocol upgrade)
                .requestMatchers("/ws", "/ws/**").permitAll()
                // All /api/executions endpoints require authentication
                .requestMatchers("/api/executions", "/api/executions/**").authenticated()
                // Explicitly deny all other routes
                .anyRequest().denyAll()
                .and()

                // OAuth2 Resource Server with JWT validation
                .oauth2ResourceServer()
                .jwt();

        return http.build();
    }
}
