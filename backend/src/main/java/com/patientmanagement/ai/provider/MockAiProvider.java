package com.patientmanagement.ai.provider;

import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.ai.dto.AiConsultationDraft;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MockAiProvider implements AiProvider {

    private static final String NOT_PROVIDED = "Not provided";

    private final AiProperties aiProperties;

    public MockAiProvider(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public AiConsultationDraft generateConsultationDraft(AiConsultationDraftInput input) {
        return new AiConsultationDraft(
                firstProvided(input.chiefComplaint(), firstSentence(input.roughNotes())),
                firstProvided(input.symptoms(), matchingSentences(input.roughNotes(), "fever", "cough", "pain", "difficulty", "symptom")),
                firstProvided(input.examination(), matchingSentences(input.roughNotes(), "temperature", "bp", "pulse", "spo2", "breathing", "vital", "exam")),
                firstProvided(input.assessment(), NOT_PROVIDED),
                firstProvided(input.treatment(), matchingSentences(input.roughNotes(), "advised", "rest", "fluid", "hydrate", "medicine", "medication", "prescribed")),
                firstProvided(input.followUpInstructions(), matchingSentences(input.roughNotes(), "follow"))
        );
    }

    @Override
    public AiPatientHistorySummaryResponse summarizePatientHistory(AiPatientHistoryInput input) {
        boolean hasClinicalHistory = !input.appointments().isEmpty()
                || !input.consultations().isEmpty()
                || !input.medicalRecords().isEmpty()
                || !input.prescriptions().isEmpty();
        String summary = hasClinicalHistory
                ? "Summary is based only on the documented records available in this system."
                : "No documented clinical history is available.";

        List<String> recentClinicalActivity = new ArrayList<>();
        recentClinicalActivity.addAll(input.consultations());
        recentClinicalActivity.addAll(input.appointments());

        return new AiPatientHistorySummaryResponse(
                summary,
                immutableItems(recentClinicalActivity),
                immutableItems(input.medicalRecords()),
                immutableItems(input.prescriptions()),
                followUpItems(input),
                providerName(),
                aiProperties.model(),
                Instant.now(),
                "AI-generated summary. Verify against the patient's records."
        );
    }

    @Override
    public String providerName() {
        return "mock";
    }

    private List<String> followUpItems(AiPatientHistoryInput input) {
        List<String> items = input.consultations().stream()
                .filter(item -> item.toLowerCase().contains("follow"))
                .toList();
        return immutableItems(items);
    }

    private List<String> immutableItems(List<String> items) {
        return items.stream()
                .filter(item -> item != null && !item.isBlank())
                .limit(5)
                .toList();
    }

    private String firstProvided(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return NOT_PROVIDED;
    }

    private String firstSentence(String value) {
        if (value == null || value.isBlank()) {
            return NOT_PROVIDED;
        }
        String[] sentences = value.trim().split("(?<=[.!?])\\s+");
        return sentences.length == 0 ? value.trim() : sentences[0].trim();
    }

    private String matchingSentences(String value, String... keywords) {
        if (value == null || value.isBlank()) {
            return NOT_PROVIDED;
        }

        List<String> matches = new ArrayList<>();
        for (String sentence : value.trim().split("(?<=[.!?])\\s+")) {
            String lowerSentence = sentence.toLowerCase();
            for (String keyword : keywords) {
                if (lowerSentence.contains(keyword)) {
                    matches.add(sentence.trim());
                    break;
                }
            }
        }
        return matches.isEmpty() ? NOT_PROVIDED : String.join("\n", matches);
    }
}
