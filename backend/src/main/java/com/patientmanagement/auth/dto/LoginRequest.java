package com.patientmanagement.auth.dto;

import com.patientmanagement.common.validation.PasswordSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(max = 255)
        String username,

        @NotBlank
        @PasswordSize
        String password
) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
