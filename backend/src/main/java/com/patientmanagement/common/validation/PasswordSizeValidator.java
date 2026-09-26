package com.patientmanagement.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordSizeValidator implements ConstraintValidator<PasswordSize, String> {
    private int minimum;

    @Override
    public void initialize(PasswordSize annotation) {
        minimum = annotation.min();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || (value.length() >= minimum && value.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
}
