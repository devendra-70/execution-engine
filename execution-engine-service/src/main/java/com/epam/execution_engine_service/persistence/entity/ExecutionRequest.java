package com.epam.execution_engine_service.persistence.entity;

import com.epam.execution_engine_service.validation.ValidMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ExecutionRequest DTO
 * Represents the incoming POST /api/executions request payload
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionRequest {
    
    @NotBlank(message = "problemId cannot be blank")
    private String problemId;
    
    @NotBlank(message = "language cannot be blank")
    private String language;
    
    @NotNull(message = "mode cannot be null")
    @NotBlank(message = "mode cannot be blank")
    @ValidMode(message = "mode must be RUN or SUBMIT")
    private String mode; // RUN or SUBMIT
    
    @NotBlank(message = "sourceCode cannot be blank")
    @Size(max = 1048576, message = "sourceCode cannot exceed 1MB (1048576 bytes)")
    private String sourceCode;
}
