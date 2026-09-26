package com.patientmanagement.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.patientmanagement.auth.dto.LoginRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class PasswordValidationTests {
    @Test
    void validatesUtf8BytesNotJustCharacters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new LoginRequest("user@example.com", "a".repeat(72)))).isEmpty();
            assertThat(validator.validate(new LoginRequest("user@example.com", "a".repeat(73)))).isNotEmpty();
            assertThat(validator.validate(new LoginRequest("user@example.com", "\u00e9".repeat(37)))).isNotEmpty();
        }
    }
}
