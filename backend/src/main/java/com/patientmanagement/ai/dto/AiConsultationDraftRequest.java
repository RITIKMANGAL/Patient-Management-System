package com.patientmanagement.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiConsultationDraftRequest(
        @NotBlank
        @Size(max = 4000)
        String roughNotes,

        @Size(max = 4000)
        String chiefComplaint,

        @Size(max = 4000)
        String symptoms,

        @Size(max = 4000)
        String examination,

        @Size(max = 4000)
        String assessment,

        @Size(max = 4000)
        String treatment,

        @Size(max = 4000)
        String followUpInstructions
) {
}
