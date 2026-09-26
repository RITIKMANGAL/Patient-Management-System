package com.patientmanagement.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    @Override
    public String toString() {
        return "TokenResponse[redacted]";
    }
}
