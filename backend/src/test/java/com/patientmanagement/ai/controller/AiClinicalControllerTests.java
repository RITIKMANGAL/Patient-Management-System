package com.patientmanagement.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.patientmanagement.ai.dto.AiConsultationDraft;
import com.patientmanagement.ai.dto.AiConsultationDraftRequest;
import com.patientmanagement.ai.dto.AiConsultationDraftResponse;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;
import com.patientmanagement.ai.service.AiClinicalService;
import com.patientmanagement.ai.service.AiProviderResponseException;
import com.patientmanagement.ai.service.AiServiceUnavailableException;
import com.patientmanagement.common.exception.GlobalExceptionHandler;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AiClinicalControllerTests {

    private AiClinicalService aiClinicalService;
    private MockMvc mockMvc;
    private UUID consultationId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        aiClinicalService = org.mockito.Mockito.mock(AiClinicalService.class);
        consultationId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(new AiClinicalController(aiClinicalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();
    }

    @Test
    void generateConsultationDraftReturnsStructuredDraft() throws Exception {
        when(aiClinicalService.generateConsultationDraft(eq(consultationId), any(AiConsultationDraftRequest.class)))
                .thenReturn(draftResponse());

        mockMvc.perform(post("/api/v1/consultations/{consultationId}/ai/draft", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roughNotes": "Patient has fever for 3 days."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.chiefComplaint").value("Fever"))
                .andExpect(jsonPath("$.notice").value("AI-generated draft - review before saving."));
    }

    @Test
    void emptyDraftInputReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/consultations/{consultationId}/ai/draft", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roughNotes": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingConsultationReturnsNotFound() throws Exception {
        when(aiClinicalService.generateConsultationDraft(eq(consultationId), any(AiConsultationDraftRequest.class)))
                .thenThrow(new ResourceNotFoundException("Consultation not found"));

        mockMvc.perform(post("/api/v1/consultations/{consultationId}/ai/draft", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roughNotes": "Patient notes"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Consultation not found"));
    }

    @Test
    void providerFailureReturnsSafeUnavailableError() throws Exception {
        when(aiClinicalService.generateConsultationDraft(eq(consultationId), any(AiConsultationDraftRequest.class)))
                .thenThrow(new AiServiceUnavailableException("AI assistance is temporarily unavailable. You can continue entering the consultation manually."));

        mockMvc.perform(post("/api/v1/consultations/{consultationId}/ai/draft", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roughNotes": "Patient notes"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("AI assistance is temporarily unavailable. You can continue entering the consultation manually."));
    }

    @Test
    void malformedProviderResponseReturnsBadGateway() throws Exception {
        when(aiClinicalService.generateConsultationDraft(eq(consultationId), any(AiConsultationDraftRequest.class)))
                .thenThrow(new AiProviderResponseException("AI response could not be processed. You can continue entering the consultation manually."));

        mockMvc.perform(post("/api/v1/consultations/{consultationId}/ai/draft", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roughNotes": "Patient notes"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("AI response could not be processed. You can continue entering the consultation manually."));
    }

    @Test
    void summarizePatientHistoryReturnsSummary() throws Exception {
        when(aiClinicalService.summarizePatientHistory(patientId)).thenReturn(summaryResponse());

        mockMvc.perform(get("/api/v1/patients/{patientId}/ai/summary", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Summary is based only on documented records."))
                .andExpect(jsonPath("$.notice").value("AI-generated summary. Verify against the patient's records."));
    }

    @Test
    void missingPatientReturnsNotFound() throws Exception {
        when(aiClinicalService.summarizePatientHistory(patientId))
                .thenThrow(new ResourceNotFoundException("Patient not found"));

        mockMvc.perform(get("/api/v1/patients/{patientId}/ai/summary", patientId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Patient not found"));
    }

    private AiConsultationDraftResponse draftResponse() {
        return new AiConsultationDraftResponse(
                new AiConsultationDraft(
                        "Fever",
                        "Cough",
                        "Temperature 100.4 F",
                        "Not provided",
                        "Rest and fluids",
                        "Not provided"
                ),
                "mock",
                "test-model",
                Instant.parse("2026-09-03T10:00:00Z"),
                "AI-generated draft - review before saving."
        );
    }

    private AiPatientHistorySummaryResponse summaryResponse() {
        return new AiPatientHistorySummaryResponse(
                "Summary is based only on documented records.",
                List.of("Consultation on 2026-09-03"),
                List.of("Medical record on 2026-09-03"),
                List.of("Prescription on 2026-09-03"),
                List.of("Follow-up: Review in one week"),
                "mock",
                "test-model",
                Instant.parse("2026-09-03T10:00:00Z"),
                "AI-generated summary. Verify against the patient's records."
        );
    }

    private LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
}
