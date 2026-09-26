package com.patientmanagement.appointment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.patientmanagement.appointment.dto.AppointmentRequest;
import com.patientmanagement.appointment.dto.AppointmentResponse;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.service.AppointmentService;
import com.patientmanagement.common.exception.GlobalExceptionHandler;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
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

class AppointmentControllerTests {

    private AppointmentService appointmentService;
    private MockMvc mockMvc;
    private UUID patientId;
    private UUID doctorId;

    @BeforeEach
    void setUp() {
        appointmentService = org.mockito.Mockito.mock(AppointmentService.class);
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(new AppointmentController(appointmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();
    }

    @Test
    void createAppointmentReturnsCreated() throws Exception {
        when(appointmentService.createAppointment(any(AppointmentRequest.class)))
                .thenReturn(response(UUID.randomUUID(), AppointmentStatus.SCHEDULED));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(AppointmentStatus.SCHEDULED)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void getAppointmentReturnsAppointment() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentService.getAppointment(appointmentId))
                .thenReturn(response(appointmentId, AppointmentStatus.CONFIRMED));

        mockMvc.perform(get("/api/v1/appointments/{id}", appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void getAppointmentsReturnsFilteredPage() throws Exception {
        when(appointmentService.getAppointments(eq(patientId), eq(doctorId), eq(AppointmentStatus.SCHEDULED), any(), any(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(response(UUID.randomUUID(), AppointmentStatus.SCHEDULED)),
                        PageRequest.of(0, 20),
                        1
                ));

        mockMvc.perform(get("/api/v1/appointments")
                        .param("patientId", patientId.toString())
                        .param("doctorId", doctorId.toString())
                        .param("status", "SCHEDULED")
                        .param("from", "2026-09-01T00:00:00")
                        .param("to", "2026-09-02T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].doctorId").value(doctorId.toString()));
    }

    @Test
    void getAppointmentsRejectsUnsupportedSortField() throws Exception {
        mockMvc.perform(get("/api/v1/appointments")
                        .param("sort", "patient.passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported sort field: patient.passwordHash"));
    }

    @Test
    void updateAppointmentReturnsUpdatedAppointment() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentService.updateAppointment(eq(appointmentId), any(AppointmentRequest.class)))
                .thenReturn(response(appointmentId, AppointmentStatus.CONFIRMED));

        mockMvc.perform(put("/api/v1/appointments/{id}", appointmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(AppointmentStatus.CONFIRMED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancelAppointmentReturnsNoContent() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        doNothing().when(appointmentService).cancelAppointment(appointmentId);

        mockMvc.perform(delete("/api/v1/appointments/{id}", appointmentId))
                .andExpect(status().isNoContent());
    }

    @Test
    void missingAppointmentReturnsNotFound() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentService.getAppointment(appointmentId))
                .thenThrow(new ResourceNotFoundException("Appointment not found"));

        mockMvc.perform(get("/api/v1/appointments/{id}", appointmentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found"));
    }

    @Test
    void invalidBusinessRuleReturnsBadRequest() throws Exception {
        when(appointmentService.createAppointment(any(AppointmentRequest.class)))
                .thenThrow(new InvalidRequestException("Doctor already has an appointment during this time slot"));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(AppointmentStatus.SCHEDULED)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Doctor already has an appointment during this time slot"));
    }

    @Test
    void invalidAppointmentRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String validRequest(AppointmentStatus status) {
        return """
                {
                  "patientId": "%s",
                  "doctorId": "%s",
                  "appointmentDateTime": "2026-09-01T10:00:00",
                  "reason": "Annual checkup",
                  "status": "%s",
                  "notes": "Bring reports"
                }
                """.formatted(patientId, doctorId, status);
    }

    private AppointmentResponse response(UUID id, AppointmentStatus status) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new AppointmentResponse(
                id,
                patientId,
                "Asha Rao",
                doctorId,
                "Kiran Shah",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "Annual checkup",
                status,
                "Bring reports",
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
