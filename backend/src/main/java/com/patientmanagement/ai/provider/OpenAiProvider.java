package com.patientmanagement.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.ai.dto.AiConsultationDraft;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class OpenAiProvider implements AiProvider {

    private static final String JSON_RESPONSE_FORMAT = "json_object";
    private static final int MAX_UNAVAILABLE_RETRIES = 2;
    private static final long INITIAL_RETRY_DELAY_MILLIS = 1000;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiProvider(AiProperties aiProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public AiConsultationDraft generateConsultationDraft(AiConsultationDraftInput input) {
        String content = completeJson(
                """
                        You organize clinician-provided notes into a consultation draft.
                        Return only JSON with fields: chiefComplaint, symptoms, examination, assessment, treatmentAdvice, followUpInstructions.
                        Use only supplied information. Do not invent diagnoses, treatments, examination findings, history, or medications.
                        If information is missing, write "Not provided".
                        This is documentation assistance only and requires clinician review.
                        """,
                """
                        Rough notes:
                        %s

                        Existing fields, if supplied:
                        Chief complaint: %s
                        Symptoms: %s
                        Examination: %s
                        Assessment: %s
                        Treatment/advice: %s
                        Follow-up instructions: %s
                        """.formatted(
                        notProvided(input.roughNotes()),
                        notProvided(input.chiefComplaint()),
                        notProvided(input.symptoms()),
                        notProvided(input.examination()),
                        notProvided(input.assessment()),
                        notProvided(input.treatment()),
                        notProvided(input.followUpInstructions())
                )
        );
        return readContent(content, AiConsultationDraft.class);
    }

    @Override
    public AiPatientHistorySummaryResponse summarizePatientHistory(AiPatientHistoryInput input) {
        String content = completeJson(
                """
                        You summarize only the supplied patient history for clinician review.
                        Return only JSON with fields: summary, recentClinicalActivity, documentedHistory, recentPrescriptions, followUp.
                        The four list fields must be arrays of strings.
                        Do not invent missing information. Do not provide diagnosis, treatment, triage, prescribing, or dosage recommendations.
                        If a category has no records, return an empty array for that category.
                        If no history is documented at all, use the summary "No documented clinical history is available."
                        """,
                """
                        Patient:
                        Name: %s
                        Date of birth: %s
                        Gender: %s

                        Appointments:
                        %s

                        Consultations:
                        %s

                        Medical records:
                        %s

                        Prescriptions:
                        %s
                        """.formatted(
                        notProvided(input.patientName()),
                        input.dateOfBirth() == null ? "Not provided" : input.dateOfBirth(),
                        notProvided(input.gender()),
                        lines(input.appointments()),
                        lines(input.consultations()),
                        lines(input.medicalRecords()),
                        lines(input.prescriptions())
                )
        );
        return readContent(content, AiPatientHistorySummaryResponse.class);
    }

    @Override
    public String providerName() {
        return "openai";
    }

    private String completeJson(String systemPrompt, String userPrompt) {
        long deadline = System.nanoTime() + Duration.ofSeconds(aiProperties.timeoutSeconds()).toNanos();
        try {
            for (int retry = 0; ; retry++) {
                Duration remaining = Duration.ofNanos(Math.max(1, deadline - System.nanoTime()));
                HttpResponse<String> response = httpClient.send(
                        buildRequest(systemPrompt, userPrompt, remaining),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (response.statusCode() == 503 && retry < MAX_UNAVAILABLE_RETRIES) {
                    long delayMillis = INITIAL_RETRY_DELAY_MILLIS << retry;
                    delayMillis += ThreadLocalRandom.current().nextLong(INITIAL_RETRY_DELAY_MILLIS);
                    if (Duration.ofMillis(delayMillis).toNanos() < deadline - System.nanoTime()) {
                        Thread.sleep(delayMillis);
                        continue;
                    }
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw providerFailure(response.statusCode());
                }
                return extractMessageContent(response.body());
            }
        } catch (AiProviderException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new AiProviderException("AI provider request timed out", exception);
        } catch (IOException exception) {
            throw new AiProviderException("AI provider request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("AI provider request interrupted", exception);
        }
    }

    private HttpRequest buildRequest(String systemPrompt, String userPrompt, Duration timeout) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", providerModel(),
                    "temperature", 0.1,
                    "response_format", Map.of("type", JSON_RESPONSE_FORMAT),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt.strip()),
                            Map.of("role", "user", "content", userPrompt.strip())
                    )
            ));

            return HttpRequest.newBuilder(providerEndpoint())
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + aiProperties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("AI provider request could not be prepared", exception);
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException("AI provider endpoint is not configured correctly", exception);
        }
    }

    private URI providerEndpoint() {
        URI configured = URI.create(aiProperties.baseUrl());
        String path = configured.getPath() == null ? "" : configured.getPath();
        if (path.endsWith("/chat/completions")) {
            return configured;
        }
        String base = configured.toString();
        return URI.create(base.endsWith("/") ? base : base + "/").resolve("chat/completions");
    }

    String providerModel() {
        URI configured = URI.create(aiProperties.baseUrl());
        String host = configured.getHost();
        if (host != null
                && host.endsWith("generativelanguage.googleapis.com")
                && !aiProperties.model().startsWith("models/")) {
            return "models/" + aiProperties.model();
        }
        return aiProperties.model();
    }

    private AiProviderException providerFailure(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new AiProviderException("AI provider authentication failed");
        }
        if (statusCode == 429) {
            return new AiProviderException("AI provider rate limit exceeded");
        }
        if (statusCode == 503) {
            return new AiProviderException("AI provider temporarily unavailable");
        }
        return new AiProviderException("AI provider returned an unsuccessful response");
    }

    private String extractMessageContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new AiProviderMalformedResponseException("AI provider response was malformed");
            }
            return content.asText();
        } catch (JsonProcessingException exception) {
            throw new AiProviderMalformedResponseException("AI provider response was malformed", exception);
        }
    }

    private <T> T readContent(String content, Class<T> type) {
        try {
            return objectMapper.readValue(content, type);
        } catch (JsonProcessingException exception) {
            throw new AiProviderMalformedResponseException("AI provider content was malformed", exception);
        }
    }

    private String lines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "None documented";
        }
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("None documented");
        return joined.isBlank() ? "None documented" : joined;
    }

    private String notProvided(String value) {
        return value == null || value.isBlank() ? "Not provided" : value.trim();
    }
}
