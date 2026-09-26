package com.patientmanagement.ai.provider;

import java.time.LocalDate;
import java.util.List;

public record AiPatientHistoryInput(
        String patientName,
        LocalDate dateOfBirth,
        String gender,
        List<String> appointments,
        List<String> consultations,
        List<String> medicalRecords,
        List<String> prescriptions
) {
}
