package com.patientmanagement.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.ai.dto.AiConsultationDraft;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAiProviderTests {

    private static final String API_KEY = "test-only-openai-key";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsExpectedRequestAndMapsConsultationDraft() throws Exception {
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = server(exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, openAiResponse(objectMapper.writeValueAsString(Map.of(
                    "chiefComplaint", "Fever",
                    "symptoms", "Cough",
                    "examination", "Temperature 100.4 F",
                    "assessment", "Not provided",
                    "treatmentAdvice", "Rest and fluids",
                    "followUpInstructions", "Review in one week"
            ))));
        });

        AiConsultationDraft draft = provider(serverUrl(), 5).generateConsultationDraft(new AiConsultationDraftInput(
                "Patient has fever and cough.",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(draft.chiefComplaint()).isEqualTo("Fever");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer " + API_KEY);
        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("gpt-test");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("messages")).hasSize(2);
        assertThat(requestBody.get()).contains("Patient has fever and cough.");
        assertThat(requestBody.get()).doesNotContain(API_KEY);
        assertThat(requestBody.get()).doesNotContain("Authorization");
        assertThat(requestBody.get()).doesNotContain("JWT");
        assertThat(requestBody.get()).doesNotContain("refresh token");
        assertThat(requestBody.get()).doesNotContain("password");
    }

    @Test
    void mapsPatientHistorySummary() throws Exception {
        server = server(exchange -> respond(exchange, 200, openAiResponse(objectMapper.writeValueAsString(Map.of(
                "summary", "Documented history is limited.",
                "recentClinicalActivity", List.of("Consultation on 2026-09-03"),
                "documentedHistory", List.of("Medical record on 2026-09-03"),
                "recentPrescriptions", List.of("Prescription on 2026-09-03"),
                "followUp", List.of("Not provided")
        )))));

        AiPatientHistorySummaryResponse response = provider(serverUrl(), 5).summarizePatientHistory(new AiPatientHistoryInput(
                "Asha Rao",
                LocalDate.of(1990, 1, 1),
                "FEMALE",
                List.of("Appointment on 2026-09-03"),
                List.of("Consultation on 2026-09-03"),
                List.of("Medical record on 2026-09-03"),
                List.of("Prescription on 2026-09-03")
        ));

        assertThat(response.summary()).isEqualTo("Documented history is limited.");
        assertThat(response.recentClinicalActivity()).containsExactly("Consultation on 2026-09-03");
    }

    @Test
    void resolvesOpenAiCompatibleBaseUrlToChatCompletionsEndpoint() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        server = server(exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            respond(exchange, 200, openAiResponse(objectMapper.writeValueAsString(Map.of(
                    "chiefComplaint", "Headache",
                    "symptoms", "Mild headache",
                    "examination", "Not provided",
                    "assessment", "Not provided",
                    "treatmentAdvice", "Rest discussed",
                    "followUpInstructions", "Review if symptoms persist"
            ))));
        });

        provider(serverRootUrl() + "/v1/", 5).generateConsultationDraft(validDraftInput());

        assertThat(requestPath.get()).isEqualTo("/v1/chat/completions");
    }

    @Test
    void usesCanonicalModelIdentifierForGeminiCompatibilityEndpoint() {
        OpenAiProvider provider = new OpenAiProvider(
                new AiProperties(
                        true,
                        "openai",
                        "gemini-3.8-flash",
                        5,
                        API_KEY,
                        "https://generativelanguage.googleapis.com/v1beta/openai/"
                ),
                objectMapper,
                HttpClient.newHttpClient()
        );

        assertThat(provider.providerModel()).isEqualTo("models/gemini-3.8-flash");
    }

    @Test
    void nonSuccessProviderResponseBecomesControlledProviderError() throws Exception {
        server = server(exchange -> respond(exchange, 401, "{\"error\":\"invalid key\"}"));

        assertThatThrownBy(() -> provider(serverUrl(), 5).generateConsultationDraft(validDraftInput()))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider authentication failed");
    }

    @Test
    void rateLimitProviderResponseBecomesControlledProviderError() throws Exception {
        server = server(exchange -> respond(exchange, 429, "{\"error\":\"rate limit\"}"));

        assertThatThrownBy(() -> provider(serverUrl(), 5).generateConsultationDraft(validDraftInput()))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider rate limit exceeded");
    }

    @Test
    void retriesClearlyTransientRateLimitsWithinTheConfiguredDeadline() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = server(exchange -> {
            if (requests.incrementAndGet() < 3) {
                respond(exchange, 429, "{\"error\":\"too many requests, retry shortly\"}");
                return;
            }
            respond(exchange, 200, openAiResponse(objectMapper.writeValueAsString(Map.of(
                    "chiefComplaint", "Headache",
                    "symptoms", "Mild headache",
                    "examination", "Not provided",
                    "assessment", "Not provided",
                    "treatmentAdvice", "Rest discussed",
                    "followUpInstructions", "Review if symptoms persist"
            ))));
        });

        AiConsultationDraft draft = provider(serverUrl(), 8).generateConsultationDraft(validDraftInput());

        assertThat(requests.get()).isEqualTo(3);
        assertThat(draft.chiefComplaint()).isEqualTo("Headache");
    }

    @Test
    void doesNotRetryQuotaExhaustionRateLimits() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = server(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 429, "{\"error\":\"quota exhausted\"}");
        });

        assertThatThrownBy(() -> provider(serverUrl(), 5).generateConsultationDraft(validDraftInput()))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider rate limit exceeded");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void retriesTemporaryUnavailabilityAndParsesSuccessfulResponse() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = server(exchange -> {
            if (requests.incrementAndGet() < 3) {
                respond(exchange, 503, "{\"error\":\"temporary outage\"}");
            } else {
                respond(exchange, 200, openAiResponse(objectMapper.writeValueAsString(Map.of(
                        "chiefComplaint", "Headache",
                        "symptoms", "Mild headache",
                        "examination", "Not provided",
                        "assessment", "Not provided",
                        "treatmentAdvice", "Rest discussed",
                        "followUpInstructions", "Review if symptoms persist"
                ))));
            }
        });

        AiConsultationDraft draft = provider(serverUrl(), 5).generateConsultationDraft(validDraftInput());

        assertThat(requests.get()).isEqualTo(3);
        assertThat(draft.chiefComplaint()).isEqualTo("Headache");
    }

    @Test
    void stopsAfterBoundedUnavailableRetriesWithoutExposingProviderBodyOrKey() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = server(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 503, "{\"error\":\"" + API_KEY + " clinical prompt\"}");
        });

        assertThatThrownBy(() -> provider(serverUrl(), 5).generateConsultationDraft(validDraftInput()))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider temporarily unavailable")
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("clinical prompt");
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryNonTransientClientErrors() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = server(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 400, "{\"error\":\"bad request\"}");
        });

        assertThatThrownBy(() -> provider(serverUrl(), 5).generateConsultationDraft(validDraftInput()))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider returned an unsuccessful response");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void malformedProviderEnvelopeIsRejected() throws Exception {
        server = server(exchange -> respond(exchange, 200, "{\"choices\":[]}"));

        assertThatThrownBy(() -> provider(serverUrl(), 5).generateConsultationDraft(validDraftInput()))
                .isInstanceOf(AiProviderMalformedResponseException.class)
                .hasMessage("AI provider response was malformed");
    }

    @Test
    void malformedModelContentIsRejected() throws Exception {
        server = server(exchange -> respond(exchange, 200, openAiResponse("not json")));

        assertThatThrownBy(() -> provider(serverUrl(), 5).generateConsultationDraft(validDraftInput()))
                .isInstanceOf(AiProviderMalformedResponseException.class)
                .hasMessage("AI provider content was malformed");
    }

    @Test
    void timeoutBecomesControlledProviderError() throws Exception {
        server = server(exchange -> {
            try {
                Thread.sleep(2_500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, openAiResponse("{}"));
        });

        assertThatThrownBy(() -> provider(serverUrl(), 1).generateConsultationDraft(validDraftInput()))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider request timed out");
    }

    @Test
    void networkFailureBecomesControlledProviderError() throws Exception {
        server = server(exchange -> respond(exchange, 200, "{}"));
        String url = serverUrl();
        server.stop(0);
        server = null;

        assertThatThrownBy(() -> provider(url, 5).generateConsultationDraft(validDraftInput()))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider request failed");
    }

    private OpenAiProvider provider(String baseUrl, long timeoutSeconds) {
        return new OpenAiProvider(
                new AiProperties(true, "openai", "gpt-test", timeoutSeconds, API_KEY, baseUrl),
                objectMapper,
                HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(timeoutSeconds)).build()
        );
    }

    private AiConsultationDraftInput validDraftInput() {
        return new AiConsultationDraftInput(
                "Patient has fever and cough.",
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/v1/chat/completions", handler::handle);
        httpServer.start();
        return httpServer;
    }

    private String serverUrl() {
        return serverRootUrl() + "/v1/chat/completions";
    }

    private String serverRootUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private String openAiResponse(String content) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", content)
                ))
        ));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
