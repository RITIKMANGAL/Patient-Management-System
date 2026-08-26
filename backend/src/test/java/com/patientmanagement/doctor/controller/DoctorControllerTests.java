package com.patientmanagement.doctor.controller;

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
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.GlobalExceptionHandler;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.dto.DoctorRequest;
import com.patientmanagement.doctor.dto.DoctorResponse;
import com.patientmanagement.doctor.service.DoctorService;
import java.time.Instant;
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

class DoctorControllerTests {

    private DoctorService doctorService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        doctorService = org.mockito.Mockito.mock(DoctorService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DoctorController(doctorService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();
    }

    @Test
    void createDoctorReturnsCreated() throws Exception {
        when(doctorService.createDoctor(any(DoctorRequest.class))).thenReturn(response(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licenseNumber").value("LIC-100"));
    }

    @Test
    void duplicateLicenseNumberReturnsConflict() throws Exception {
        when(doctorService.createDoctor(any(DoctorRequest.class)))
                .thenThrow(new DuplicateResourceException("Doctor license number already exists"));

        mockMvc.perform(post("/api/v1/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Doctor license number already exists"));
    }

    @Test
    void getDoctorReturnsDoctor() throws Exception {
        UUID id = UUID.randomUUID();
        when(doctorService.getDoctor(id)).thenReturn(response(id));

        mockMvc.perform(get("/api/v1/doctors/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void listDoctorsReturnsPage() throws Exception {
        when(doctorService.getDoctors(any()))
                .thenReturn(new PageImpl<>(java.util.List.of(response(UUID.randomUUID())), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("Kiran"));
    }

    @Test
    void updateDoctorReturnsUpdatedDoctor() throws Exception {
        UUID id = UUID.randomUUID();
        when(doctorService.updateDoctor(eq(id), any(DoctorRequest.class))).thenReturn(response(id));

        mockMvc.perform(put("/api/v1/doctors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteDoctorReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(doctorService).deleteDoctor(id);

        mockMvc.perform(delete("/api/v1/doctors/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void missingDoctorReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(doctorService.getDoctor(id)).thenThrow(new ResourceNotFoundException("Doctor not found"));

        mockMvc.perform(get("/api/v1/doctors/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Doctor not found"));
    }

    private String validRequest() {
        return """
                {
                  "firstName": "Kiran",
                  "lastName": "Shah",
                  "specialization": "Cardiology",
                  "licenseNumber": "LIC-100",
                  "phone": "+15555550200",
                  "email": "synthetic.doctor@example.com",
                  "department": "Cardiology"
                }
                """;
    }

    private DoctorResponse response(UUID id) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        return new DoctorResponse(
                id,
                "Kiran",
                "Shah",
                "Cardiology",
                "LIC-100",
                "+15555550200",
                "synthetic.doctor@example.com",
                "Cardiology",
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
