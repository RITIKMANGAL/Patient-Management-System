package com.patientmanagement.demo.config;

import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.communication.config.SmsProperties;
import java.util.Locale;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "demo.mode", name = "enabled", havingValue = "true")
public class DemoEnvironmentValidator implements SmartInitializingSingleton {

    private final DemoModeProperties demoModeProperties;
    private final DemoDataProperties demoDataProperties;
    private final DemoAiProperties demoAiProperties;
    private final AiProperties aiProperties;
    private final SmsProperties smsProperties;
    private final DataSourceProperties dataSourceProperties;

    public DemoEnvironmentValidator(
            DemoModeProperties demoModeProperties,
            DemoDataProperties demoDataProperties,
            DemoAiProperties demoAiProperties,
            AiProperties aiProperties,
            SmsProperties smsProperties,
            DataSourceProperties dataSourceProperties
    ) {
        this.demoModeProperties = demoModeProperties;
        this.demoDataProperties = demoDataProperties;
        this.demoAiProperties = demoAiProperties;
        this.aiProperties = aiProperties;
        this.smsProperties = smsProperties;
        this.dataSourceProperties = dataSourceProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate(
                demoModeProperties,
                demoDataProperties,
                demoAiProperties,
                aiProperties,
                smsProperties,
                dataSourceProperties.getUrl(),
                dataSourceProperties.getUsername()
        );
    }

    static void validate(
            DemoModeProperties demoMode,
            DemoDataProperties demoData,
            DemoAiProperties demoAi,
            AiProperties ai,
            SmsProperties sms,
            String databaseUrl,
            String databaseUsername
    ) {
        if (!demoMode.enabled()) {
            return;
        }
        if (!demoData.enabled()) {
            throw new IllegalStateException("DEMO_MODE requires DEMO_DATA_ENABLED=true");
        }
        if (!isDemoDatabase(databaseUrl) || !"clinora_demo".equals(databaseUsername)) {
            throw new IllegalStateException("DEMO_MODE requires the dedicated clinora_demo database and user");
        }
        if (sms.enabled()) {
            throw new IllegalStateException("DEMO_MODE requires SMS_ENABLED=false");
        }
        if (!ai.enabled() || !"openai".equalsIgnoreCase(ai.provider()) || ai.apiKey().isBlank()) {
            throw new IllegalStateException("DEMO_MODE requires an enabled real OpenAI-compatible AI provider");
        }
        if (!demoAi.enabled()) {
            throw new IllegalStateException("DEMO_MODE requires demo AI protections");
        }
    }

    private static boolean isDemoDatabase(String databaseUrl) {
        if (databaseUrl == null) {
            return false;
        }
        String normalized = databaseUrl.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("jdbc:postgresql://[^/]+/clinora_demo(?:[?].*)?");
    }
}
