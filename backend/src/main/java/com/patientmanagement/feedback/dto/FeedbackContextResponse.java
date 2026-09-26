package com.patientmanagement.feedback.dto;

import java.time.LocalDateTime;

public record FeedbackContextResponse(
        String doctorName,
        LocalDateTime appointmentDateTime,
        boolean submitted
) {
}
