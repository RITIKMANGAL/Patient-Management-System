package com.patientmanagement.ai.dto;

import java.time.Instant;
import java.util.List;

public record AiPatientHistorySummaryResponse(
        String summary,
        List<String> recentClinicalActivity,
        List<String> documentedHistory,
        List<String> recentPrescriptions,
        List<String> followUp,
        String provider,
        String model,
        Instant generatedAt,
        String notice
) {
}
