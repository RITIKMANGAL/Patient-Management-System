package com.patientmanagement.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.ai.dto.AiConsultationDraft;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class RealAiProviderSmokeTests {

    @Test
    @EnabledIfEnvironmentVariable(named = "AI_LIVE_TEST", matches = "true")
    void generatesFreshDraftThroughConfiguredProvider() {
        AiProperties properties = new AiProperties(
                true,
                required("AI_PROVIDER"),
                required("AI_MODEL"),
                Long.parseLong(required("AI_TIMEOUT_SECONDS")),
                required("AI_API_KEY"),
                required("AI_BASE_URL")
        );
        OpenAiProvider provider = new OpenAiProvider(
                properties,
                new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.timeoutSeconds())).build()
        );

        AiConsultationDraft draft = provider.generateConsultationDraft(new AiConsultationDraftInput(
                "Patient reports mild tension-type headache for two days. Symptoms are improving with rest. "
                        + "No other documented symptoms. Verification reference CLINORA-LIVE-20260924.",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        String combined = String.join(" ",
                draft.chiefComplaint(),
                draft.symptoms(),
                draft.examination(),
                draft.assessment(),
                draft.treatmentAdvice(),
                draft.followUpInstructions()
        ).toLowerCase(Locale.ENGLISH);
        assertThat(provider.providerName()).isEqualTo("openai");
        assertThat(combined).contains("headache");
        assertThat(combined).doesNotContain("fever for 3 days");
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the live AI smoke test");
        }
        return value;
    }
}
