package com.patientmanagement.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.ai.dto.AiConsultationDraft;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockAiProviderTests {

    private final MockAiProvider provider = new MockAiProvider(new AiProperties(
            true,
            "mock",
            "test-model",
            5,
            "",
            "https://example.test/chat"
    ));

    @Test
    void generatesConsultationDraftFromProvidedNotesWithoutInventingAssessment() {
        AiConsultationDraft draft = provider.generateConsultationDraft(new AiConsultationDraftInput(
                "Patient has fever for 3 days. Temperature 100.4 F. Advised rest and fluids.",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(draft.chiefComplaint()).isEqualTo("Patient has fever for 3 days.");
        assertThat(draft.symptoms()).contains("fever");
        assertThat(draft.examination()).contains("Temperature 100.4 F.");
        assertThat(draft.assessment()).isEqualTo("Not provided");
        assertThat(draft.treatmentAdvice()).contains("Advised rest and fluids.");
    }

    @Test
    void summarizesEmptyHistoryWithClearEmptyState() {
        AiPatientHistorySummaryResponse response = provider.summarizePatientHistory(new AiPatientHistoryInput(
                "Asha Rao",
                LocalDate.of(1990, 1, 1),
                "FEMALE",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(response.summary()).isEqualTo("No documented clinical history is available.");
        assertThat(response.recentClinicalActivity()).isEmpty();
        assertThat(response.documentedHistory()).isEmpty();
        assertThat(response.recentPrescriptions()).isEmpty();
    }
}
