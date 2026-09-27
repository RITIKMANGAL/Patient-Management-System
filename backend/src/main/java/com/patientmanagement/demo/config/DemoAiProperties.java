package com.patientmanagement.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.ai")
public record DemoAiProperties(
        boolean enabled,
        int maxRequestsPerMinute,
        int maxConcurrentRequests,
        int maxPromptCharacters,
        int maxOutputCharacters
) {

    public DemoAiProperties {
        if (maxRequestsPerMinute < 1 || maxConcurrentRequests < 1
                || maxPromptCharacters < 1 || maxOutputCharacters < 1) {
            throw new IllegalStateException("Demo AI limits must be positive");
        }
    }
}
