package com.epam.execution_engine_service.persistence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for JPA, Hibernate, and database connectivity.
 * Implements SRS §12 application configuration:
 * - Batch size: 50 records
 * - Order inserts: true (for FK integrity in batches)
 * - DDL validation: validate mode (schema already exists)
 * - Isolation: READ_COMMITTED per SRS §5.1
 *
 * Configuration is externalized to application.properties to support
 * environment-specific overrides.
 */
@Configuration
public class JpaConfig {

    /**
     * ObjectMapper bean for JSON serialization/deserialization.
     * Used by Redis publishing and REST endpoints.
     *
     * Configuration:
     * - Java 8+ date/time support (OffsetDateTime, Instant, etc.)
     * - ISO-8601 format for timestamps
     * - Strict null handling
     *
     * @return configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        final ObjectMapper mapper = new ObjectMapper();

        // Register Java 8+ date/time module for OffsetDateTime serialization
        mapper.registerModule(new JavaTimeModule());

        // Use ISO-8601 format for dates (RFC 3339 for OffsetDateTime)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
