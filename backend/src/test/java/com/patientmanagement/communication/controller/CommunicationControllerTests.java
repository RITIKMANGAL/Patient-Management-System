package com.patientmanagement.communication.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.patientmanagement.common.exception.GlobalExceptionHandler;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.communication.dto.CommunicationResponse;
import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationStatus;
import com.patientmanagement.communication.model.CommunicationType;
import com.patientmanagement.communication.service.CommunicationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CommunicationControllerTests {

    private CommunicationService communicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communicationService = org.mockito.Mockito.mock(CommunicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CommunicationController(communicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();
    }

    @Test
    void getCommunicationsReturnsPagedCommunicationHistory() throws Exception {
        when(communicationService.getCommunications(any()))
                .thenReturn(new PageImpl<>(
                        List.of(response(UUID.randomUUID())),
                        PageRequest.of(0, 20),
                        1
                ));

        mockMvc.perform(get("/api/v1/communications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("PRESCRIPTION_AVAILABLE"))
                .andExpect(jsonPath("$.content[0].channel").value("SMS"))
                .andExpect(jsonPath("$.content[0].status").value("SENT"));
    }

    @Test
    void getCommunicationReturnsCommunication() throws Exception {
        UUID communicationId = UUID.randomUUID();
        when(communicationService.getCommunication(communicationId)).thenReturn(response(communicationId));

        mockMvc.perform(get("/api/v1/communications/{id}", communicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(communicationId.toString()))
                .andExpect(jsonPath("$.recipient").value("+15555550100"));
    }

    @Test
    void missingCommunicationReturnsNotFound() throws Exception {
        UUID communicationId = UUID.randomUUID();
        when(communicationService.getCommunication(communicationId))
                .thenThrow(new ResourceNotFoundException("Communication not found"));

        mockMvc.perform(get("/api/v1/communications/{id}", communicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Communication not found"));
    }

    @Test
    void unsupportedCommunicationSortReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/communications")
                        .param("sort", "patient.passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported sort field: patient.passwordHash"));
    }

    private CommunicationResponse response(UUID id) {
        Instant now = Instant.parse("2026-08-28T10:00:00Z");
        return new CommunicationResponse(
                id,
                UUID.randomUUID(),
                "Asha Rao",
                null,
                CommunicationType.PRESCRIPTION_AVAILABLE,
                CommunicationChannel.SMS,
                "+15555550100",
                CommunicationStatus.SENT,
                "provider-100",
                null,
                1,
                now,
                null,
                now,
                now
        );
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
}
