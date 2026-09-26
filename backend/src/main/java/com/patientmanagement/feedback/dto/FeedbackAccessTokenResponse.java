package com.patientmanagement.feedback.dto;

import java.time.Instant;

public record FeedbackAccessTokenResponse(
        String token,
        String feedbackUrl,
        Instant expiresAt
) {

    @Override
    public String toString() {
        return "FeedbackAccessTokenResponse[token=<redacted>, feedbackUrl=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
