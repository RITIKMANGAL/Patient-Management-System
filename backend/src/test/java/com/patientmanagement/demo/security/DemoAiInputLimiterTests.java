package com.patientmanagement.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patientmanagement.ai.dto.AiConsultationDraftRequest;
import com.patientmanagement.demo.config.DemoAiProperties;
import org.junit.jupiter.api.Test;

class DemoAiInputLimiterTests {

    @Test
    void enforcesTheConfiguredTotalPromptBudgetOnlyInDemoMode() {
        DemoAiInputLimiter limiter = new DemoAiInputLimiter(new DemoAiProperties(true, 3, 1, 10, 100));
        AiConsultationDraftRequest oversized = new AiConsultationDraftRequest("12345678901", null, null, null, null, null, null);

        assertThatThrownBy(() -> limiter.requireDraftWithinLimit(oversized))
                .hasMessage("AI demo input exceeds the configured prompt limit");

        DemoAiInputLimiter standard = new DemoAiInputLimiter(new DemoAiProperties(false, 3, 1, 10, 100));
        assertThatCode(() -> standard.requireDraftWithinLimit(oversized)).doesNotThrowAnyException();
        assertThat(limiter.maxOutputCharacters()).isEqualTo(100);
    }
}
