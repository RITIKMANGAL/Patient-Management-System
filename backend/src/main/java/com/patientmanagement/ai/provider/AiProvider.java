package com.patientmanagement.ai.provider;

import com.patientmanagement.ai.dto.AiConsultationDraft;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;

public interface AiProvider {

    AiConsultationDraft generateConsultationDraft(AiConsultationDraftInput input);

    AiPatientHistorySummaryResponse summarizePatientHistory(AiPatientHistoryInput input);

    String providerName();
}
