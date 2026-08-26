package com.patientmanagement.prescription.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(
        UUID id,
        UUID patientId,
        String patientName,
        UUID doctorId,
        String doctorName,
        LocalDate prescriptionDate,
        String notes,
        List<PrescriptionItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
