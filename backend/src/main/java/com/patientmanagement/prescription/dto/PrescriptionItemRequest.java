package com.patientmanagement.prescription.dto;

import com.patientmanagement.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrescriptionItemRequest(
        @NotBlank
        @Size(max = 200)
        @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.TEXT_WITH_LETTER, message = "must contain letters")
        String medicineName,

        @NotBlank
        @Size(max = 100)
        String dosage,

        @NotBlank
        @Size(max = 100)
        String frequency,

        @NotBlank
        @Size(max = 100)
        String duration,

        @Size(max = 500)
        String instructions
) {
}
