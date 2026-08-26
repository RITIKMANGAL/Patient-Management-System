package com.patientmanagement.medicalrecord.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MedicalRecordResponse(
        UUID id,
        UUID patientId,
        String patientName,
        UUID doctorId,
        String doctorName,
        String diagnosis,
        String symptoms,
        String notes,
        LocalDate recordDate,
        Instant createdAt,
        Instant updatedAt
) {
}
