package com.patientmanagement.consultation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.GlobalExceptionHandler;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.consultation.dto.ConsultationResponse;
import com.patientmanagement.consultation.dto.ConsultationUpdateRequest;
import com.patientmanagement.consultation.model.ConsultationStatus;
import com.patientmanagement.consultation.service.ConsultationService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ConsultationControllerTests {

    private ConsultationService consultationService;
    private MockMvc mockMvc;
    private UUID appointmentId;
    private UUID consultationId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        consultationService = org.mockito.Mockito.mock(ConsultationService.class);
        appointmentId = UUID.randomUUID();
        consultationId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(new ConsultationController(consultationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();
    }

    @Test
    void startConsultationReturnsCreated() throws Exception {
        when(consultationService.startConsultation(appointmentId)).thenReturn(response(ConsultationStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/v1/appointments/{appointmentId}/consultation", appointmentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointmentId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void duplicateConsultationReturnsConflict() throws Exception {
        when(consultationService.startConsultation(appointmentId))
                .thenThrow(new DuplicateResourceException("Consultation already exists for appointment"));

        mockMvc.perform(post("/api/v1/appointments/{appointmentId}/consultation", appointmentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Consultation already exists for appointment"));
    }

    @Test
    void getAppointmentConsultationReturnsConsultation() throws Exception {
        when(consultationService.getAppointmentConsultation(appointmentId))
                .thenReturn(response(ConsultationStatus.IN_PROGRESS));

        mockMvc.perform(get("/api/v1/appointments/{appointmentId}/consultation", appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(appointmentId.toString()));
    }

    @Test
    void missingConsultationReturnsNotFound() throws Exception {
        when(consultationService.getConsultation(consultationId))
                .thenThrow(new ResourceNotFoundException("Consultation not found"));

        mockMvc.perform(get("/api/v1/consultations/{id}", consultationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Consultation not found"));
    }

    @Test
    void updateConsultationReturnsUpdatedConsultation() throws Exception {
        when(consultationService.updateConsultation(eq(consultationId), any(ConsultationUpdateRequest.class)))
                .thenReturn(response(ConsultationStatus.IN_PROGRESS));

        mockMvc.perform(put("/api/v1/consultations/{id}", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chiefComplaint").value("Chest pain"));
    }

    @Test
    void updateConsultationValidationReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/consultations/{id}", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chiefComplaint": "%s"
                                }
                                """.formatted("x".repeat(4001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeConsultationReturnsCompletedConsultation() throws Exception {
        when(consultationService.completeConsultation(consultationId))
                .thenReturn(response(ConsultationStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/consultations/{id}/complete", consultationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void invalidCompletionReturnsBadRequest() throws Exception {
        when(consultationService.completeConsultation(consultationId))
                .thenThrow(new InvalidRequestException("Chief complaint is required before completion"));

        mockMvc.perform(post("/api/v1/consultations/{id}/complete", consultationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chief complaint is required before completion"));
    }

    @Test
    void getPatientConsultationsReturnsPage() throws Exception {
        when(consultationService.getPatientConsultations(eq(patientId), any()))
                .thenReturn(new PageImpl<>(List.of(response(ConsultationStatus.COMPLETED)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/patients/{patientId}/consultations", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].patientId").value(patientId.toString()));
    }

    @Test
    void unsupportedConsultationSortReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{patientId}/consultations", patientId)
                        .param("sort", "appointment.patient.passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported sort field: appointment.patient.passwordHash"));
    }

    private String updateRequest() {
        return """
                {
                  "chiefComplaint": "Chest pain",
                  "symptoms": "Fatigue",
                  "examination": "Stable vitals",
                  "assessment": "Musculoskeletal pain",
                  "treatment": "Rest and hydration",
                  "followUpInstructions": "Review in one week"
                }
                """;
    }

    private ConsultationResponse response(ConsultationStatus status) {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        return new ConsultationResponse(
                consultationId,
                appointmentId,
                patientId,
                "Asha Rao",
                UUID.randomUUID(),
                "Kiran Shah",
                LocalDateTime.of(2026, 9, 3, 10, 0),
                status == ConsultationStatus.COMPLETED ? AppointmentStatus.COMPLETED : AppointmentStatus.CONFIRMED,
                status,
                "Chest pain",
                "Fatigue",
                "Stable vitals",
                "Musculoskeletal pain",
                "Rest and hydration",
                "Review in one week",
                now,
                status == ConsultationStatus.COMPLETED ? now : null,
                now,
                now
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
