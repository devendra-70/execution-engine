package com.epam.execution_engine_service.gateway.rest;

import com.epam.execution_engine_service.gateway.rest.dev.DevTokenController;
import com.epam.execution_engine_service.gateway.security.JwtProperties;
import com.epam.execution_engine_service.gateway.security.JwtTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DevTokenController.class)
@ActiveProfiles("dev")
@DisplayName("DevTokenController Unit Tests")
class DevTokenControllerTest {

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
        when(jwtProperties.getSecretKey())
                .thenReturn("my-super-secret-unit-test-key-that-is-long-enough-for-hmac-256!!");
    }

    @Nested
    @DisplayName("Token Generation Endpoint Tests")
    class TokenGenerationTests {

        @Test
        @WithMockUser
        @DisplayName("Should generate JWT token with default userId")
        void testGenerateToken_DefaultUserId() throws Exception {
            mockMvc.perform(get("/api/dev/token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.userId").value("1"))
                    .andExpect(jsonPath("$.expiresIn").value("3600s"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should accept custom userId parameter")
        void testGenerateToken_CustomUserId() throws Exception {
            mockMvc.perform(get("/api/dev/token").param("userId", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("99"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should accept custom subject parameter")
        void testGenerateToken_CustomSubject() throws Exception {
            mockMvc.perform(get("/api/dev/token").param("subject", "customuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return non-empty JWT token string")
        void testGenerateToken_TokenNotEmpty() throws Exception {
            mockMvc.perform(get("/api/dev/token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", notNullValue()));
        }

        @Test
        @WithMockUser
        @DisplayName("Should always include 3600s expiration")
        void testGenerateToken_ExpiresIn3600() throws Exception {
            mockMvc.perform(get("/api/dev/token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expiresIn").value("3600s"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should handle large userId values")
        void testGenerateToken_LargeUserId() throws Exception {
            mockMvc.perform(get("/api/dev/token").param("userId", "999999999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("999999999"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should generate valid JWT structure (three parts separated by dots)")
        void testGenerateToken_ValidJWTStructure() throws Exception {
            mockMvc.perform(get("/api/dev/token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value(
                            matchesPattern("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
                    ));
        }
    }
}
