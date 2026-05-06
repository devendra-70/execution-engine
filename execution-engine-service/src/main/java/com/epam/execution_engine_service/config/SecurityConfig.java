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
 * <p>Configures:
 * <ul>
 *   <li>OAuth2 Resource Server with JWT validation</li>
 *   <li>Method-level authorization via @PreAuthorize</li>
 *   <li>Public endpoints (health, WebSocket)</li>
 *   <li>Stateless session management for REST API</li>
 * </ul>
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
     * <p>Setup:
     * <ul>
     *   <li>CSRF disabled (stateless REST API)</li>
     *   <li>Stateless session management (no session cookies)</li>
     *   <li>Public endpoints: /actuator/health, /ws/** (WebSocket)</li>
     *   <li>All other endpoints require authentication</li>
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

                // Authorization rules
                .authorizeHttpRequests()
                // Health check endpoint (no auth required)
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // WebSocket endpoint (permitted; JWT validation happens at protocol upgrade)
                .requestMatchers("/ws/**").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
                .and()

                // OAuth2 Resource Server with JWT validation
                .oauth2ResourceServer()
                .jwt();

        return http.build();
    }
}
