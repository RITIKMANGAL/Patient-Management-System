package com.patientmanagement.prescription.access.dto;

import java.time.Instant;

public record PrescriptionAccessStatusResponse(
        boolean active,
        Instant expiresAt
) {
}
