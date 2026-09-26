package com.patientmanagement.medicalrecord.dto;

import com.patientmanagement.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record MedicalRecordRequest(
        @NotNull
        UUID patientId,

        @NotNull
        UUID doctorId,

        @NotBlank
        @Size(max = 500)
        @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.TEXT_WITH_LETTER, message = "must contain letters")
        String diagnosis,

        @Size(max = 1000)
        String symptoms,

        @Size(max = 5000)
        String notes,

        @NotNull
        @PastOrPresent
        LocalDate recordDate
) {
}
