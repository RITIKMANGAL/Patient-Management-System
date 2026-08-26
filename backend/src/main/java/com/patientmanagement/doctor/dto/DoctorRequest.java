package com.patientmanagement.doctor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DoctorRequest(
        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotBlank
        @Size(max = 150)
        String specialization,

        @NotBlank
        @Size(max = 100)
        String licenseNumber,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 .()\\-]{7,25}$", message = "must be a valid phone number")
        String phone,

        @Email
        @Size(max = 255)
        String email,

        @Size(max = 150)
        String department
) {
}
