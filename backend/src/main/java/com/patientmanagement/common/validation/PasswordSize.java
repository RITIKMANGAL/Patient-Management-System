package com.patientmanagement.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordSizeValidator.class)
public @interface PasswordSize {
    String message() default "must meet the minimum length and contain at most 72 UTF-8 bytes";
    int min() default 1;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
