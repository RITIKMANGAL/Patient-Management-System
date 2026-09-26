package com.patientmanagement.prescription.access.dto;

import java.time.Instant;

public record PrescriptionAccessTokenResponse(
        String token,
        Instant expiresAt
) {

    @Override
    public String toString() {
        return "PrescriptionAccessTokenResponse[token=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
