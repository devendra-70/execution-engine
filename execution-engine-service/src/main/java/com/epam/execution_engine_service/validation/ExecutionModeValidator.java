package com.epam.execution_engine_service.validation;

import com.epam.execution_engine_service.model.ExecutionMode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Validator implementation for the @ValidMode constraint.
 *
 * <p>Validates that a string value can be converted to a valid ExecutionMode enum.
 * Conversion is case-insensitive.
 *
 * @author Execution Engine Team
 */
@Slf4j
public class ExecutionModeValidator implements ConstraintValidator<ValidMode, String> {

    @Override
    public void initialize(ValidMode annotation) {
        log.debug("Initializing ExecutionModeValidator");
    }

    /**
     * Validates that the provided string value is a valid ExecutionMode.
     *
     * @param value the string value to validate (from request)
     * @param context the constraint validator context
     * @return true if value is null (let @NotBlank handle it) or a valid ExecutionMode;
     *         false if value is not a valid ExecutionMode
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null values are handled by @NotBlank; skip validation here
        if (value == null) {
            return true;
        }

        try {
            // Try to convert string to ExecutionMode enum (case-insensitive)
            ExecutionMode.valueOf(value.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            log.debug("Invalid execution mode value: {}", value);
            return false;
        }
    }
}
