package de.iot.hub.telemetry.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FiniteValidator implements ConstraintValidator<Finite, Double> {
    @Override
    public boolean isValid(Double value, ConstraintValidatorContext context) {
        return value == null || Double.isFinite(value);
    }
}
