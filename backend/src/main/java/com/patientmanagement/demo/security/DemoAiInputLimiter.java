package com.patientmanagement.demo.security;

import com.patientmanagement.ai.dto.AiConsultationDraftRequest;
import com.patientmanagement.ai.provider.AiPatientHistoryInput;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.demo.config.DemoAiProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoAiInputLimiter {

    private final DemoAiProperties properties;

    public DemoAiInputLimiter(DemoAiProperties properties) {
        this.properties = properties;
    }

    public void requireDraftWithinLimit(AiConsultationDraftRequest request) {
        if (properties.enabled() && totalLength(
                request.roughNotes(), request.chiefComplaint(), request.symptoms(), request.examination(),
                request.assessment(), request.treatment(), request.followUpInstructions()) > properties.maxPromptCharacters()) {
            throw new InvalidRequestException("AI demo input exceeds the configured prompt limit");
        }
    }

    public void requireSummaryWithinLimit(AiPatientHistoryInput input) {
        if (properties.enabled() && totalLength(
                input.patientName(), String.valueOf(input.dateOfBirth()), input.gender(),
                joined(input.appointments()), joined(input.consultations()), joined(input.medicalRecords()),
                joined(input.prescriptions())) > properties.maxPromptCharacters()) {
            throw new InvalidRequestException("AI demo input exceeds the configured prompt limit");
        }
    }

    public int maxOutputCharacters() {
        return properties.enabled() ? properties.maxOutputCharacters() : Integer.MAX_VALUE;
    }

    private String joined(List<String> values) {
        return values == null ? "" : String.join("", values);
    }

    private int totalLength(String... values) {
        int total = 0;
        for (String value : values) {
            if (value != null) {
                total += value.length();
            }
        }
        return total;
    }
}
