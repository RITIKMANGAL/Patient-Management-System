package com.patientmanagement.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.auth.security.CorsProperties;
import com.patientmanagement.communication.config.SmsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionSafetyConfigTests {
    private void configure(String origin, boolean sms, boolean ai) {
        new ProductionSafetyConfig(new CorsProperties(List.of(origin)),
                new AiProperties(ai, "mock", "mock", 15, "", "https://api.example.com"),
                new SmsProperties(sms, "noop", null, 15, null),
                "https://clinic.example.com", "https://clinic.example.com",
                "unit-fixture-not-a-real-secret", "unit-fixture-not-a-password");
    }

    @Test
    void requiresExplicitHttpsAndDisabledUnverifiedProviders() {
        assertThatCode(() -> configure("https://clinic.example.com", false, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> configure("http://localhost:5173", false, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> configure("https://clinic.example.com", true, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> configure("https://clinic.example.com", false, true)).isInstanceOf(IllegalStateException.class);
    }
}
