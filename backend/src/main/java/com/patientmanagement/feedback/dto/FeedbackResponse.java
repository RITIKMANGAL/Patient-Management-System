package com.patientmanagement.feedback.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        UUID consultationId,
        UUID patientId,
        String patientName,
        UUID doctorId,
        String doctorName,
        LocalDateTime appointmentDateTime,
        Integer rating,
        String comment,
        Instant createdAt
) {
}
