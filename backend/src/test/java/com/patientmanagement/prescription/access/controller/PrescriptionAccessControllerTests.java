package com.patientmanagement.prescription.access.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.patientmanagement.common.exception.GlobalExceptionHandler;
import com.patientmanagement.prescription.access.dto.PrescriptionAccessTokenResponse;
import com.patientmanagement.prescription.access.dto.PrescriptionAccessStatusResponse;
import com.patientmanagement.prescription.access.service.PrescriptionAccessDeniedException;
import com.patientmanagement.prescription.access.service.PrescriptionAccessTokenService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PrescriptionAccessControllerTests {

    private PrescriptionAccessTokenService accessTokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accessTokenService = org.mockito.Mockito.mock(PrescriptionAccessTokenService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PrescriptionAccessController(accessTokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new ResourceHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(objectMapper())
                )
                .build();
    }

    @Test
    void createPrescriptionAccessReturnsRawTokenOnce() throws Exception {
        UUID prescriptionId = UUID.randomUUID();
        when(accessTokenService.createAccessToken(prescriptionId)).thenReturn(new PrescriptionAccessTokenResponse(
                "secure-token",
                Instant.parse("2026-09-13T00:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/prescriptions/{prescriptionId}/access", prescriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("secure-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-13T00:00:00Z"));
    }

    @Test
    void revokePrescriptionAccessReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/prescriptions/{prescriptionId}/access", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    void activePrescriptionAccessStatusDoesNotExposeToken() throws Exception {
        UUID prescriptionId = UUID.randomUUID();
        when(accessTokenService.getActiveAccessStatus(prescriptionId)).thenReturn(
                new PrescriptionAccessStatusResponse(true, Instant.parse("2026-09-13T00:00:00Z"))
        );

        mockMvc.perform(get("/api/v1/prescriptions/{prescriptionId}/access", prescriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-13T00:00:00Z"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void publicPrescriptionAccessReturnsPdf() throws Exception {
        when(accessTokenService.generatePrescriptionPdf("secure-token")).thenReturn("%PDF-1.7 synthetic".getBytes());

        mockMvc.perform(get("/api/v1/prescription-access/{token}/pdf", "secure-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"prescription.pdf\""));
    }

    @Test
    void publicPrescriptionAccessRejectsInvalidTokenGenerically() throws Exception {
        when(accessTokenService.generatePrescriptionPdf("invalid-token")).thenThrow(new PrescriptionAccessDeniedException());

        mockMvc.perform(get("/api/v1/prescription-access/{token}/pdf", "invalid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Prescription access link is invalid or expired"));
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
}
