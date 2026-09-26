package com.patientmanagement.auth.dto;

import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.common.validation.PasswordSize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RegisterRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String username,

        @NotBlank
        @PasswordSize(min = 8)
        String password,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotNull
        RoleName role,

        UUID doctorId
) {
    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}
