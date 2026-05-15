package com.epam.execution_engine_service.gateway.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviTokenController.class)
@ActiveProfiles("dev")
@DisplayName("DeviTokenController Unit Tests")
class DeviTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Object jwtProperties;

    @Nested
    @DisplayName("Token Generation Endpoint Tests")
    class TokenGenerationTests {

        @Test
        @DisplayName("Should generate JWT token with default userId")
        void testGenerateToken_DefaultUserId() throws Exception {
            mockMvc.perform(get("/api/dev/token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.userId").value("1"))
                    .andExpect(jsonPath("$.expiresIn").value("3600s"));
        }

        @Test
        @DisplayName("Should accept custom userId parameter")
        void testGenerateToken_CustomUserId() throws Exception {
            mockMvc.perform(get("/api/dev/token").param("userId", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("99"));
        }

        @Test
        @DisplayName("Should accept custom subject parameter")
        void testGenerateToken_CustomSubject() throws Exception {
            mockMvc.perform(get("/api/dev/token").param("subject", "customuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }

        @Test
        @DisplayName("Should return non-empty JWT token string")
        void testGenerateToken_TokenNotEmpty() throws Exception {
            mockMvc.perform(get("/api/dev/token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", hasLength(greaterThan(50))));
        }

        @Test
        @DisplayName("Should always include 3600s expiration")
        void testGenerateToken_ExpiresIn3600() throws Exception {
            mockMvc.perform(get("/api/dev/token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expiresIn").value("3600s"));
        }

        @Test
        @DisplayName("Should handle large userId values")
        void testGenerateToken_LargeUserId() throws Exception {
            mockMvc.perform(get("/api/dev/token").param("userId", "999999999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("999999999"));
        }

        @Test
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
