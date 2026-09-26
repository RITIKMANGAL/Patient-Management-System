package com.patientmanagement.communication.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.patientmanagement.communication.provider.CommunicationProvider;
import com.patientmanagement.communication.provider.NoOpSmsCommunicationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SmsProviderConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SmsProviderConfiguration.class);

    @Test
    void selectsNoOpProviderWhenSmsIsEnabledAndProviderIsNoop() {
        contextRunner
                .withPropertyValues("communication.sms.enabled=true")
                .withBean(SmsProperties.class, () -> smsProperties(true, "noop"))
                .run(context -> {
                    assertThat(context).hasSingleBean(CommunicationProvider.class);
                    assertThat(context.getBean(CommunicationProvider.class)).isInstanceOf(NoOpSmsCommunicationProvider.class);
                });
    }

    @Test
    void doesNotCreateProviderWhenSmsIsDisabled() {
        contextRunner
                .withPropertyValues("communication.sms.enabled=false")
                .withBean(SmsProperties.class, () -> smsProperties(false, "noop"))
                .run(context -> assertThat(context).doesNotHaveBean(CommunicationProvider.class));
    }

    @Test
    void rejectsUnsupportedProviderUntilARealProviderIsImplemented() {
        contextRunner
                .withPropertyValues("communication.sms.enabled=true")
                .withBean(SmsProperties.class, () -> smsProperties(true, "msg91"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("Unsupported SMS provider configured: msg91"));
    }

    private SmsProperties smsProperties(boolean enabled, String provider) {
        return new SmsProperties(
                enabled,
                provider,
                "PMCLINIC",
                15,
                new SmsProperties.Templates(null, null, null, null, null)
        );
    }
}
