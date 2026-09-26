package com.patientmanagement.config;

import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.auth.security.CorsProperties;
import com.patientmanagement.communication.config.SmsProperties;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod")
public class ProductionSafetyConfig {
    public ProductionSafetyConfig(CorsProperties cors, AiProperties ai, SmsProperties sms,
            @Value("${feedback.access.public-base-url}") String feedbackUrl,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${spring.datasource.password}") String databasePassword) {
        if (cors.allowedOrigins() == null || cors.allowedOrigins().isEmpty()) {
            throw new IllegalStateException("Production CORS origins are required");
        }
        cors.allowedOrigins().forEach(ProductionSafetyConfig::requireHttps);
        requireHttps(feedbackUrl);
        requireHttps(frontendUrl);
        requireCredential(jwtSecret);
        requireCredential(databasePassword);
        if (sms.enabled()) {
            throw new IllegalStateException("Real SMS is not implemented; SMS must be disabled in production");
        }
        if (ai.enabled()) {
            if (!"openai".equals(ai.provider())) {
                throw new IllegalStateException("Production AI must use a real provider or be disabled");
            }
            requireHttps(ai.baseUrl());
            requireCredential(ai.apiKey());
        }
    }

    private static void requireCredential(String value) {
        String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank() || normalized.contains("placeholder") || normalized.contains("replace")
                || normalized.contains("change-me") || normalized.contains("test-only")) {
            throw new IllegalStateException("Production credentials must be explicitly configured");
        }
    }

    private static void requireHttps(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Production URLs must be explicit HTTPS URLs");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null
                || uri.getHost().equalsIgnoreCase("localhost") || uri.getHost().equals("127.0.0.1")
                || uri.getHost().equals("[::1]")) {
            throw new IllegalStateException("Production URLs must be explicit non-local HTTPS URLs");
        }
    }
}
