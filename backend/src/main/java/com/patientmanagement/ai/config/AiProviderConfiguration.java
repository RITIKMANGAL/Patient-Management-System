package com.patientmanagement.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientmanagement.ai.provider.AiProvider;
import com.patientmanagement.ai.provider.MockAiProvider;
import com.patientmanagement.ai.provider.OpenAiProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfiguration {

    private static final String MOCK_PROVIDER = "mock";
    private static final String OPENAI_PROVIDER = "openai";

    @Bean
    public AiProvider aiProvider(AiProperties aiProperties, ObjectMapper objectMapper) {
        if (!aiProperties.enabled()) {
            return new MockAiProvider(aiProperties);
        }

        String provider = aiProperties.provider().trim().toLowerCase();
        if (MOCK_PROVIDER.equals(provider)) {
            return new MockAiProvider(aiProperties);
        }
        if (OPENAI_PROVIDER.equals(provider)) {
            if (aiProperties.apiKey().isBlank()) {
                throw new IllegalStateException("AI provider 'openai' requires AI_API_KEY");
            }
            return new OpenAiProvider(
                    aiProperties,
                    objectMapper,
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(aiProperties.timeoutSeconds()))
                            .build()
            );
        }

        throw new IllegalStateException("Unsupported AI provider configured: " + aiProperties.provider());
    }
}
