package com.epam.execution_engine_service.gateway.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SRP: HTTP filter only — decides whether to apply rate-limiting and writes the 429 response.
 * DIP: Delegates rate-limit enforcement to {@link RateLimitService}, not Redis directly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Only rate-limit the submission endpoint
        if (!"/api/executions".equals(request.getRequestURI())
                || !"POST".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);
        if (!rateLimitService.tryConsume(key)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Build a per-user key (authenticated) or per-IP key (anonymous). */
    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return "ratelimit:user:" + auth.getName();
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) ip = request.getRemoteAddr();
        return "ratelimit:ip:" + ip;
    }
}
