package com.patientmanagement.doctor.dto;

import java.time.Instant;
import java.util.UUID;

public record DoctorResponse(
        UUID id,
        String firstName,
        String lastName,
        String specialization,
        String licenseNumber,
        String phone,
        String email,
        String department,
        Instant createdAt,
        Instant updatedAt
) {
}
