package com.epam.execution_engine_service.gateway.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimitFilter, "requestsPerMinute", 5);
        responseWriter = new StringWriter();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_nonPostRequest_passesThrough() throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("GET");

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void doFilterInternal_nonExecutionEndpoint_passesThrough() throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/other");
        when(request.getMethod()).thenReturn("POST");

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void doFilterInternal_rateLimitNotExceededWithAuthenticatedUser_incrementsAndPasses() 
            throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(valueOperations, times(1)).get("ratelimit:user:testuser");
        verify(valueOperations, times(1)).increment("ratelimit:user:testuser");
        verify(redisTemplate, times(1)).expire(eq("ratelimit:user:testuser"), any(Duration.class));
        verify(filterChain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(429);

        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_rateLimitNotExceededWithExistingCount_incrementsAndPasses() 
            throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn("2");
        when(valueOperations.increment(anyString())).thenReturn(3L);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        SecurityContextHolder.setContext(new SecurityContextImpl());

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(valueOperations, times(1)).get("ratelimit:ip:192.168.1.1");
        verify(valueOperations, times(1)).increment("ratelimit:ip:192.168.1.1");
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        verify(filterChain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void doFilterInternal_rateLimitExceeded_returns429() throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn("5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

        PrintWriter printWriter = new PrintWriter(responseWriter);
        when(response.getWriter()).thenReturn(printWriter);

        SecurityContextHolder.setContext(new SecurityContextImpl());

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response, times(1)).setStatus(429);
        verify(filterChain, never()).doFilter(request, response);
        verify(valueOperations, never()).increment(anyString());
        
        printWriter.flush();
        assertThat(responseWriter.toString()).contains("Rate limit exceeded");
    }

    @Test
    void doFilterInternal_unauthenticatedUserWithXForwardedFor_usesProxiedIp() 
            throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");

        SecurityContextHolder.setContext(new SecurityContextImpl());

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(valueOperations, times(1)).get("ratelimit:ip:203.0.113.5");
        verify(valueOperations, times(1)).increment("ratelimit:ip:203.0.113.5");
        verify(redisTemplate, times(1)).expire(eq("ratelimit:ip:203.0.113.5"), any(Duration.class));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_unauthenticatedUserWithoutXForwardedFor_usesRemoteAddr() 
            throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.100.50");

        SecurityContextHolder.setContext(new SecurityContextImpl());

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(valueOperations, times(1)).get("ratelimit:ip:192.168.100.50");
        verify(valueOperations, times(1)).increment("ratelimit:ip:192.168.100.50");
        verify(redisTemplate, times(1)).expire(eq("ratelimit:ip:192.168.100.50"), any(Duration.class));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_setsTTLOnlyForFirstRequest_notForSubsequentRequests() 
            throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn("1");
        when(valueOperations.increment(anyString())).thenReturn(2L);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        SecurityContextHolder.setContext(new SecurityContextImpl());

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(valueOperations, times(1)).increment("ratelimit:ip:192.168.1.100");
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_rateLimitBoundary_atExactLimit() throws ServletException, IOException {
        // Arrange
        ReflectionTestUtils.setField(rateLimitFilter, "requestsPerMinute", 3);
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn("2");
        when(valueOperations.increment(anyString())).thenReturn(3L);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        SecurityContextHolder.setContext(new SecurityContextImpl());

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void doFilterInternal_rateLimitBoundary_exceedsLimit() throws ServletException, IOException {
        // Arrange
        ReflectionTestUtils.setField(rateLimitFilter, "requestsPerMinute", 3);
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn("3");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        PrintWriter printWriter = new PrintWriter(responseWriter);
        when(response.getWriter()).thenReturn(printWriter);

        SecurityContextHolder.setContext(new SecurityContextImpl());

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response, times(1)).setStatus(429);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_authenticatedUserPrioritized_overIpKey() throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("john_doe");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(valueOperations, times(1)).get("ratelimit:user:john_doe");
        verify(valueOperations, never()).get("ratelimit:ip:10.0.0.1");
        verify(valueOperations, times(1)).increment("ratelimit:user:john_doe");
        verify(filterChain, times(1)).doFilter(request, response);

        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_nullAuthentication_fallsBackToIp() throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("172.16.0.1");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(valueOperations, times(1)).get("ratelimit:ip:172.16.0.1");
        verify(valueOperations, times(1)).increment("ratelimit:ip:172.16.0.1");
        verify(filterChain, times(1)).doFilter(request, response);

        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_unauthenticatedUser_fallsBackToIp() throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/executions");
        when(request.getMethod()).thenReturn("POST");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.20.30.40");

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(valueOperations, times(1)).get("ratelimit:ip:10.20.30.40");
        verify(valueOperations, times(1)).increment("ratelimit:ip:10.20.30.40");
        verify(filterChain, times(1)).doFilter(request, response);

        SecurityContextHolder.clearContext();
    }
}
