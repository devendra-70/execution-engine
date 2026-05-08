package com.epam.execution_engine_service.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validator annotation for ExecutionMode enum values.
 *
 * <p>Validates that a string field can be converted to a valid ExecutionMode enum value.
 * Conversion is case-insensitive (e.g., "run", "RUN", "Run" are all valid).
 *
 * <p>Usage:
 * <pre>
 *     @NotBlank
 *     @ValidMode(message = "mode must be RUN or SUBMIT")
 *     private String mode;
 * </pre>
 *
 * @author Execution Engine Team
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExecutionModeValidator.class)
@Documented
public @interface ValidMode {

    String message() default "Invalid execution mode. Must be RUN or SUBMIT.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
