package com.epam.execution_engine_service.gateway.security;

import com.epam.execution_engine_service.gateway.ratelimit.RateLimitFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityConfig — Unit Tests")
class SecurityConfigTest {

    @Mock
    private JwtAuthFilter jwtAuthFilter;

    @Mock
    private RateLimitFilter rateLimitFilter;

    @InjectMocks
    private SecurityConfig securityConfig;

    /** Retrieve the resolved CORS config for a given path. */
    private CorsConfiguration corsForPath(String uri) {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        return source.getCorsConfiguration(req);
    }

    // -----------------------------------------------------------------------
    // Bean instantiation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Bean instantiation")
    class BeanInstantiationTests {

        @Test
        @DisplayName("SecurityConfig is created with injected filters")
        void instantiatedWithDependencies() {
            assertThat(securityConfig).isNotNull();
            assertThat(securityConfig).hasFieldOrPropertyWithValue("jwtAuthFilter", jwtAuthFilter);
            assertThat(securityConfig).hasFieldOrPropertyWithValue("rateLimitFilter", rateLimitFilter);
        }

        @Test
        @DisplayName("applicationStartup() returns a BufferingApplicationStartup")
        void applicationStartupBeanType() {
            ApplicationStartup startup = securityConfig.applicationStartup();
            assertThat(startup)
                    .isNotNull()
                    .isInstanceOf(BufferingApplicationStartup.class);
        }

        @Test
        @DisplayName("corsConfigurationSource() returns an UrlBasedCorsConfigurationSource")
        void corsSourceType() {
            CorsConfigurationSource corsSource = securityConfig.corsConfigurationSource();
            assertThat(corsSource).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        }

        @Test
        @DisplayName("SecurityConfig carries @Configuration and @EnableWebSecurity")
        void securityConfigAnnotations() {
            assertThat(SecurityConfig.class)
                    .hasAnnotation(org.springframework.context.annotation.Configuration.class)
                    .hasAnnotation(org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class);
        }
    }

    // -----------------------------------------------------------------------
    // CORS configuration values
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CORS configuration values")
    class CorsConfigurationValueTests {

        @Test
        @DisplayName("Allows wildcard origin patterns")
        void allowedOriginPatterns() {
            assertThat(corsForPath("/api/test").getAllowedOriginPatterns()).contains("*");
        }

        @Test
        @DisplayName("Allows GET, POST, PUT, DELETE, OPTIONS")
        void allowedMethods() {
            assertThat(corsForPath("/api/test").getAllowedMethods())
                    .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
        }

        @Test
        @DisplayName("Allows all request headers (*)")
        void allowedHeaders() {
            assertThat(corsForPath("/api/test").getAllowedHeaders()).contains("*");
        }

        @Test
        @DisplayName("Exposes all response headers (*)")
        void exposedHeaders() {
            assertThat(corsForPath("/api/test").getExposedHeaders()).contains("*");
        }

        @Test
        @DisplayName("Allow-Credentials is true")
        void allowCredentials() {
            assertThat(corsForPath("/api/test").getAllowCredentials()).isTrue();
        }

        @Test
        @DisplayName("CORS config is registered for all paths (/**)")
        void registeredForAllPaths() {
            for (String path : List.of(
                    "/api/executions",
                    "/api/dev/token",
                    "/ws/stomp",
                    "/actuator/health")) {
                assertThat(corsForPath(path))
                        .as("Expected CORS config for path: %s", path)
                        .isNotNull();
            }
        }
    }
}

