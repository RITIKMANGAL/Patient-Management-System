package com.patientmanagement.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientmanagement.ai.provider.AiProvider;
import com.patientmanagement.ai.provider.MockAiProvider;
import com.patientmanagement.ai.provider.OpenAiProvider;
import org.junit.jupiter.api.Test;

class AiProviderConfigurationTests {

    private final AiProviderConfiguration configuration = new AiProviderConfiguration();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void selectsMockProviderWhenConfigured() {
        AiProvider provider = configuration.aiProvider(
                new AiProperties(true, "mock", "test-model", 5, "", "https://example.test/chat"),
                objectMapper
        );

        assertThat(provider).isInstanceOf(MockAiProvider.class);
        assertThat(provider.providerName()).isEqualTo("mock");
    }

    @Test
    void disabledAiUsesMockProviderWithoutRequiringRealProviderSecrets() {
        AiProvider provider = configuration.aiProvider(
                new AiProperties(false, "openai", "test-model", 5, "", "https://example.test/chat"),
                objectMapper
        );

        assertThat(provider).isInstanceOf(MockAiProvider.class);
    }

    @Test
    void selectsOpenAiProviderWhenConfiguredWithApiKey() {
        AiProvider provider = configuration.aiProvider(
                new AiProperties(true, "openai", "gpt-test", 5, "test-only-key", "https://example.test/chat"),
                objectMapper
        );

        assertThat(provider).isInstanceOf(OpenAiProvider.class);
        assertThat(provider.providerName()).isEqualTo("openai");
    }

    @Test
    void rejectsOpenAiProviderWithoutApiKey() {
        assertThatThrownBy(() -> configuration.aiProvider(
                new AiProperties(true, "openai", "gpt-test", 5, "", "https://example.test/chat"),
                objectMapper
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI provider 'openai' requires AI_API_KEY");
    }

    @Test
    void rejectsUnsupportedProviderInsteadOfFallingBackToMock() {
        assertThatThrownBy(() -> configuration.aiProvider(
                new AiProperties(true, "unexpected", "test-model", 5, "", "https://example.test/chat"),
                objectMapper
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported AI provider configured: unexpected");
    }
}
