package com.patientmanagement.communication.config;

import com.patientmanagement.communication.provider.CommunicationProvider;
import com.patientmanagement.communication.provider.NoOpSmsCommunicationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsProviderConfiguration {

    private static final String NOOP_PROVIDER = "noop";

    @Bean
    @ConditionalOnProperty(
            prefix = "communication.sms",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public CommunicationProvider smsCommunicationProvider(SmsProperties smsProperties) {
        if (NOOP_PROVIDER.equals(smsProperties.provider())) {
            return new NoOpSmsCommunicationProvider();
        }
        throw new IllegalStateException(
                "Unsupported SMS provider configured: " + smsProperties.provider() + ". No real SMS provider is integrated yet."
        );
    }
}
