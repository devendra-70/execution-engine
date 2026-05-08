package com.epam.execution_engine_service.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ExecutionRegistrationResponse DTO
 * HTTP 202 response body
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionRegistrationResponse {
    private String executionId;
    private String status; // PENDING
    private String submittedAt; // ISO-8601 format
}
