package com.patientmanagement.doctor.dto;

import com.patientmanagement.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DoctorRequest(
        @NotBlank
        @Size(max = 100)
        @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.PERSON_NAME, message = "must be a valid name")
        String firstName,

        @NotBlank
        @Size(max = 100)
        @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.PERSON_NAME, message = "must be a valid name")
        String lastName,

        @NotBlank
        @Size(max = 150)
        @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.TEXT_WITH_LETTER, message = "must contain letters")
        String specialization,

        @NotBlank
        @Size(min = 3, max = 100)
        @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.LICENSE_NUMBER, message = "must be a valid license number")
        String licenseNumber,

        @NotBlank
        @Pattern(regexp = ValidationPatterns.PHONE, message = "must be a valid phone number")
        String phone,

        @Email
        @Size(max = 255)
        String email,

        @Size(max = 150)
        @jakarta.validation.constraints.Pattern(regexp = "^$|" + ValidationPatterns.TEXT_WITH_LETTER, message = "must contain letters")
        String department
) {
}
