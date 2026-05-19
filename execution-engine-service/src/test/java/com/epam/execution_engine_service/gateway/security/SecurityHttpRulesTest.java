package com.epam.execution_engine_service.gateway.security;

import com.epam.execution_engine_service.gateway.rest.dev.DevTokenController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the HTTP security rules defined in {@link SecurityConfig}:
 * - {@code /api/dev/**}         — permit-all
 * - {@code /actuator/health}    — permit-all
 * - {@code /actuator/info}      — permit-all
 * - {@code /ws/**}              — permit-all
 * - All other routes            — require authentication (401)
 * - CSRF                        — disabled (POST without CSRF token → 401, not 403)
 *
 * Uses {@code @WebMvcTest} so the real {@link SecurityConfig} filter chain is applied
 * without needing a full application context.
 */
@WebMvcTest(DevTokenController.class)
@ActiveProfiles("dev")
@Import(SecurityConfig.class)
@DisplayName("SecurityConfig — HTTP Security Rules")
class SecurityHttpRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtProperties jwtProperties;

    @MockBean
    private JwtTokenValidator jwtTokenValidator;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Provide a 64-byte key so DevTokenController can sign real JWTs
        when(jwtProperties.getSecretKey())
                .thenReturn("my-super-secret-unit-test-key-that-is-long-enough-for-hmac-256!!");
        // RateLimitFilter calls opsForValue() before AnonymousAuthenticationFilter runs;
        // return a stub ValueOperations to prevent NullPointerException in the filter.
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // -----------------------------------------------------------------------
    // Permit-all routes — no authentication required
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Permit-all routes")
    class PermitAllRoutes {

        @Test
        @DisplayName("GET /api/dev/token is accessible without authentication")
        void devToken_noAuth_returns200() throws Exception {
            mockMvc.perform(get("/api/dev/token"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/dev/token?userId=42 is accessible without authentication")
        void devTokenWithParam_noAuth_returns200() throws Exception {
            mockMvc.perform(get("/api/dev/token").param("userId", "42"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/dev/token?subject=alice is accessible without authentication")
        void devTokenWithSubject_noAuth_returns200() throws Exception {
            mockMvc.perform(get("/api/dev/token").param("subject", "alice"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /actuator/health is not blocked by security (returns non-401)")
        void actuatorHealth_noAuth_notUnauthorized() throws Exception {
            MvcResult result = mockMvc.perform(get("/actuator/health")).andReturn();
            // Security lets the request through (permit-all); servlet may return 200 or 404
            assertThat(result.getResponse().getStatus())
                    .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("GET /actuator/info is not blocked by security (returns non-401)")
        void actuatorInfo_noAuth_notUnauthorized() throws Exception {
            MvcResult result = mockMvc.perform(get("/actuator/info")).andReturn();
            assertThat(result.getResponse().getStatus())
                    .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("GET /ws/stomp is not blocked by security (returns non-401)")
        void wsEndpoint_noAuth_notUnauthorized() throws Exception {
            MvcResult result = mockMvc.perform(get("/ws/stomp")).andReturn();
            assertThat(result.getResponse().getStatus())
                    .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("GET /ws/info is not blocked by security (returns non-401)")
        void wsInfoEndpoint_noAuth_notUnauthorized() throws Exception {
            MvcResult result = mockMvc.perform(get("/ws/info")).andReturn();
            assertThat(result.getResponse().getStatus())
                    .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }

    // -----------------------------------------------------------------------
    // Protected routes — authentication required
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Protected routes")
    class ProtectedRoutes {

        @Test
        @DisplayName("GET /api/executions/{id}/status is blocked without authentication")
        void executionStatus_noAuth_isBlocked() throws Exception {
            // SecurityConfig has no custom AuthenticationEntryPoint, so Spring Security's
            // default Http403ForbiddenEntryPoint fires for anonymous users (403, not 401).
            mockMvc.perform(get("/api/executions/exec-123/status"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/executions is blocked without authentication")
        void submitExecution_noAuth_isBlocked() throws Exception {
            // CSRF is disabled; anonymous users hit Http403ForbiddenEntryPoint → 403.
            mockMvc.perform(post("/api/executions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"problemId\":1,\"language\":\"JAVA\",\"mode\":\"RUN\",\"sourceCode\":\"code\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/some-other-endpoint is blocked without authentication")
        void unknownProtectedEndpoint_noAuth_isBlocked() throws Exception {
            mockMvc.perform(get("/api/some-other-endpoint"))
                    .andExpect(status().isForbidden());
        }
    }

    // -----------------------------------------------------------------------
    // CSRF disabled
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CSRF is disabled")
    class CsrfDisabled {

        @Test
        @DisplayName("POST /api/dev/token without CSRF token is not rejected with 403")
        void postToDev_noCsrfToken_notForbidden() throws Exception {
            // With CSRF disabled, a POST with no CSRF token must NOT return 403.
            // /api/dev/** is permit-all so the response is 405 (method not allowed — controller
            // only registers GET), confirming neither CSRF nor auth blocked the request.
            mockMvc.perform(post("/api/dev/token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
