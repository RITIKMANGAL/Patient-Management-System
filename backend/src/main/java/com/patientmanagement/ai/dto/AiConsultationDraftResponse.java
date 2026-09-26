package com.patientmanagement.ai.dto;

import java.time.Instant;

public record AiConsultationDraftResponse(
        AiConsultationDraft draft,
        String provider,
        String model,
        Instant generatedAt,
        String notice
) {
}
