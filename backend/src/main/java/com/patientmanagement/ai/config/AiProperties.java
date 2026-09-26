package com.patientmanagement.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String model,
        long timeoutSeconds,
        String apiKey,
        String baseUrl
) {

    public AiProperties {
        provider = isBlank(provider) ? "mock" : provider;
        model = isBlank(model) ? "mock-clinical-assistant-v1" : model;
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = isBlank(baseUrl) ? "https://api.openai.com/v1/chat/completions" : baseUrl;
        if (timeoutSeconds < 1) {
            throw new IllegalStateException("AI timeout must be at least 1 second");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return "AiProperties[redacted]";
    }
}
