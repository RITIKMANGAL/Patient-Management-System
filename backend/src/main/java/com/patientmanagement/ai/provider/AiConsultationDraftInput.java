package com.patientmanagement.ai.provider;

public record AiConsultationDraftInput(
        String roughNotes,
        String chiefComplaint,
        String symptoms,
        String examination,
        String assessment,
        String treatment,
        String followUpInstructions
) {
}
