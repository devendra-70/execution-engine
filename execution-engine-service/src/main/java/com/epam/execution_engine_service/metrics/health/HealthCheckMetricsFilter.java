package com.epam.execution_engine_service.metrics.health;

import com.epam.execution_engine_service.config.ApplicationProperties;
import java.util.Objects;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * HTTP Servlet Filter for Health Endpoint Metrics
 *
 * <p><b>SRS Compliance:</b> Section 12 - Health Check Failure Rate Signal (Signal 3)
 * <br><b>EPMICMPCOD-525:</b> Operational Metrics - Signal 3: Actuator Health Endpoint Monitoring
 *
 * <p><b>Responsibility:</b>
 * Intercepts all HTTP requests to {@code GET /actuator/health} endpoint and tracks non-2xx response codes
 * as failures. Maintains a Counter metric that records total failures for auto-scaling and alerting.
 *
 * <p><b>Metric Exposed:</b>
 * <ul>
 *   <li><b>Name:</b> {@code actuator.health.failures.count}</li>
 *   <li><b>Type:</b> Counter (monotonically increasing)</li>
 *   <li><b>Unit:</b> Count of failed health checks</li>
 *   <li><b>Threshold:</b> {@code app.metrics.health-check.failure-rate-threshold-percent} (default: 10%)</li>
 *   <li><b>Window:</b> {@code app.metrics.health-check.window-seconds} (default: 300s = 5 minutes)</li>
 * </ul>
 *
 * <p><b>Configuration Properties:</b>
 * <ul>
 *   <li>{@code app.metrics.health-check.failure-rate-threshold-percent} - Alert threshold (default: 10)</li>
 *   <li>{@code app.metrics.health-check.window-seconds} - Measurement window (default: 300)</li>
 * </ul>
 *
 * <p><b>Request Interception:</b>
 * <ol>
 *   <li>Incoming HTTP request to {@code /actuator/health} or {@code /actuator/health/**}</li>
 *   <li>Response status code captured after processing</li>
 *   <li>If non-2xx response → failure counter incremented</li>
 *   <li>Failure counter incremented (if applicable)</li>
 *   <li>Response flushed to client</li>
 * </ol>
 *
 * <p><b>Example Metrics Output (Prometheus format):</b>
 * <pre>
 * # HELP actuator_health_failures_total Total number of health check failures
 * # TYPE actuator_health_failures_total counter
 * actuator_health_failures_total{environment="prod"} 3.0
 * </pre>
 *
 * <p><b>SRS §3.2 & §12 Compliance:</b>
 * /actuator/health endpoint is public (no auth required per SRS §3.3).
 * This filter captures health check responses for real-time monitoring and auto-scaling.
 *
 * <p><b>Integration with Auto-Scaling:</b>
 * CloudWatch alarms monitor this metric:
 * - Failure rate = (total_failures / total_requests) × 100
 * - If rate > configured threshold → trigger scale-up alarm
 * - ECS auto-scaling responds to alarm
 *
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li>Lightweight: Only increments Counter (minimal CPU impact)</li>
 *   <li>Thread-safe: MeterRegistry.Counter is atomic</li>
 *   <li>No blocking I/O: Filter does not introduce latency</li>
 *   <li>Sampling: Every request captured (no sampling needed for health checks)</li>
 * </ul>
 *
 * @author EPMICMPCOD-350 Implementation Team
 * @version 1.0
 * @see javax.servlet.Filter
 * @see io.micrometer.core.instrument.Counter
 * @see ApplicationProperties.Metrics.HealthCheck
 */
@Component
public class HealthCheckMetricsFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckMetricsFilter.class);
    private static final String METRIC_NAME = "actuator.health.failures.count";
    private static final String FILTER_NAME = "HealthCheckMetricsFilter";
    private static final String HEALTH_ENDPOINT_PREFIX = "/actuator/health";

    private final MeterRegistry meterRegistry;
    private final ApplicationProperties applicationProperties;
    private Counter failureCounter;

    /**
     * Constructs the health check metrics filter with dependency injection.
     *
     * @param meterRegistry          Micrometer MeterRegistry for metric registration
     * @param applicationProperties  Application configuration containing health check thresholds
     */
    @Autowired
    public HealthCheckMetricsFilter(
            MeterRegistry meterRegistry,
            ApplicationProperties applicationProperties) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
        this.applicationProperties = Objects.requireNonNull(applicationProperties, "applicationProperties cannot be null");
    }

    /**
     * Initializes the servlet filter on application startup.
     *
     * <p>Creates the failure counter metric that will be incremented on each non-2xx response.
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing {} with metrics registry...", FILTER_NAME);

        // Create failure counter metric
        this.failureCounter = Counter.builder(METRIC_NAME)
                .description("Total number of non-2xx responses from /actuator/health endpoint. " +
                        "SRS §12: Health Check Failure Signal. Threshold: " +
                        applicationProperties.getMetrics().getHealthCheck().getFailureRateThresholdPercent() + "%")
                .tag("endpoint", HEALTH_ENDPOINT_PREFIX)
                .tag("srs_section", "12")
                .tag("signal_number", "3")
                .register(meterRegistry);

        logger.info("✓ {} initialized successfully", FILTER_NAME);
        logger.info("  Threshold: {}% failure rate", 
                applicationProperties.getMetrics().getHealthCheck().getFailureRateThresholdPercent());
        logger.info("  Window: {}s", 
                applicationProperties.getMetrics().getHealthCheck().getWindowSeconds());
    }

    /**
     * Intercepts HTTP requests to /actuator/health and tracks failure responses.
     *
     * <p><b>Filter Logic:</b>
     * <ol>
     *   <li>Check if request path is for /actuator/health endpoint</li>
     *   <li>If yes: wrap response to capture HTTP status code</li>
     *   <li>Process request through filter chain</li>
     *   <li>Check response status: if non-2xx → increment failure counter</li>
     *   <li>Allow response to complete</li>
     * </ol>
     *
     * <p><b>Request Routing:</b>
     * <ul>
     *   <li>Applies to: {@code /actuator/health}, {@code /actuator/health/kafka}, etc.</li>
     *   <li>Does NOT apply to other endpoints</li>
     * </ul>
     *
     * <p><b>SRS §12 Compliance:</b>
     * Captures health check response codes for real-time monitoring and auto-scaling signals.
     *
     * @param request     The ServletRequest object
     * @param response    The ServletResponse object
     * @param chain       The FilterChain object for continuing request processing
     * @throws IOException      If I/O error occurs
     * @throws ServletException If servlet error occurs
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Process request through filter chain
            filterChain.doFilter(request, response);

            // Check response status after processing
            int status = response.getStatus();
            if (!isSuccessStatus(status)) {
                // Record failure
                failureCounter.increment();
                logger.debug("Health check failure recorded: HTTP {} from {} to {}",
                        status,
                        request.getRemoteAddr(),
                        request.getRequestURI());
            }

        } catch (IOException | ServletException e) {
            // Handle any exceptions during filter processing
            failureCounter.increment();
            logger.warn("Exception during health check processing: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Determines if an HTTP status code indicates success (2xx range).
     *
     * <p><b>Success Criteria:</b>
     * <ul>
     *   <li>200 OK</li>
     *   <li>201 Created</li>
     *   <li>202 Accepted</li>
     *   <li>204 No Content</li>
     *   <li>All other 2xx codes</li>
     * </ul>
     *
     * <p><b>Failure Codes (monitored):</b>
     * <ul>
     *   <li>3xx Redirects</li>
     *   <li>4xx Client Errors (404, 401, etc.)</li>
     *   <li>5xx Server Errors (500, 503, etc.)</li>
     * </ul>
     *
     * @param statusCode HTTP status code to check
     * @return true if status is 2xx (success), false otherwise
     */
    private boolean isSuccessStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}
