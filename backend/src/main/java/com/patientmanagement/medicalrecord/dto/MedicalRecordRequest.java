package com.patientmanagement.medicalrecord.dto;

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
        String diagnosis,

        @Size(max = 1000)
        String symptoms,

        String notes,

        @NotNull
        @PastOrPresent
        LocalDate recordDate
) {
}
