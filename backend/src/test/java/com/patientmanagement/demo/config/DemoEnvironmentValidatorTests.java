package com.patientmanagement.demo.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.communication.config.SmsProperties;
import org.junit.jupiter.api.Test;

class DemoEnvironmentValidatorTests {

    @Test
    void acceptsOnlyTheDedicatedDemoDatabaseWithRealAiAndDisabledSms() {
        assertThatCode(() -> DemoEnvironmentValidator.validate(
                demoMode(), demoData(), demoAi(), realAi(), sms(false),
                "jdbc:postgresql://postgres:5432/clinora_demo", "clinora_demo"
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsProductionLookingOrNonDemoDatabase() {
        assertThatThrownBy(() -> DemoEnvironmentValidator.validate(
                demoMode(), demoData(), demoAi(), realAi(), sms(false),
                "jdbc:postgresql://postgres:5432/patient_management", "patient_management"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DEMO_MODE requires the dedicated clinora_demo database and user");
    }

    @Test
    void rejectsDemoWithoutSeedRealAiProtectionOrDisabledSms() {
        assertThatThrownBy(() -> DemoEnvironmentValidator.validate(
                demoMode(), new DemoDataProperties(false, "demo-password"), demoAi(), realAi(), sms(false),
                "jdbc:postgresql://postgres:5432/clinora_demo", "clinora_demo"
        )).hasMessage("DEMO_MODE requires DEMO_DATA_ENABLED=true");

        assertThatThrownBy(() -> DemoEnvironmentValidator.validate(
                demoMode(), demoData(), new DemoAiProperties(false, 3, 1, 1800, 1600), realAi(), sms(false),
                "jdbc:postgresql://postgres:5432/clinora_demo", "clinora_demo"
        )).hasMessage("DEMO_MODE requires demo AI protections");

        assertThatThrownBy(() -> DemoEnvironmentValidator.validate(
                demoMode(), demoData(), demoAi(), realAi(), sms(true),
                "jdbc:postgresql://postgres:5432/clinora_demo", "clinora_demo"
        )).hasMessage("DEMO_MODE requires SMS_ENABLED=false");
    }

    @Test
    void leavesNonDemoConfigurationUntouched() {
        assertThatCode(() -> DemoEnvironmentValidator.validate(
                new DemoModeProperties(false, ""), new DemoDataProperties(false, ""),
                new DemoAiProperties(false, 3, 1, 1800, 1600),
                new AiProperties(false, "mock", "mock", 5, "", "https://example.test"), sms(true),
                "jdbc:postgresql://postgres:5432/patient_management", "patient_management"
        )).doesNotThrowAnyException();
    }

    private DemoModeProperties demoMode() {
        return new DemoModeProperties(true, "demo-admin@clinora.app");
    }

    private DemoDataProperties demoData() {
        return new DemoDataProperties(true, "demo-password");
    }

    private DemoAiProperties demoAi() {
        return new DemoAiProperties(true, 3, 1, 1800, 1600);
    }

    private AiProperties realAi() {
        return new AiProperties(true, "openai", "gemini-3.8-flash", 30, "demo-key", "https://example.test");
    }

    private SmsProperties sms(boolean enabled) {
        return new SmsProperties(enabled, "noop", "CLINORA", 15, null);
    }
}
