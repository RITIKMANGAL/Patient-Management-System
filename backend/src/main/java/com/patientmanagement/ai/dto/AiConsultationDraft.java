package com.patientmanagement.ai.dto;

public record AiConsultationDraft(
        String chiefComplaint,
        String symptoms,
        String examination,
        String assessment,
        String treatmentAdvice,
        String followUpInstructions
) {
}
