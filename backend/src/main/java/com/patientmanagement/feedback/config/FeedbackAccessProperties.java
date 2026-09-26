package com.patientmanagement.feedback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feedback.access")
public record FeedbackAccessProperties(
        long tokenExpirationHours,
        String publicBaseUrl
) {

    public FeedbackAccessProperties {
        if (tokenExpirationHours < 1) {
            throw new IllegalStateException("Feedback access token expiration must be at least 1 hour");
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("Feedback public base URL is required");
        }
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }
}
