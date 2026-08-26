package com.patientmanagement.prescription.controller;

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
import com.patientmanagement.common.exception.GlobalExceptionHandler;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.prescription.dto.PrescriptionItemResponse;
import com.patientmanagement.prescription.dto.PrescriptionRequest;
import com.patientmanagement.prescription.dto.PrescriptionResponse;
import com.patientmanagement.prescription.service.PrescriptionService;
import java.time.Instant;
import java.time.LocalDate;
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

class PrescriptionControllerTests {

    private PrescriptionService prescriptionService;
    private MockMvc mockMvc;
    private UUID patientId;
    private UUID doctorId;

    @BeforeEach
    void setUp() {
        prescriptionService = org.mockito.Mockito.mock(PrescriptionService.class);
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(new PrescriptionController(prescriptionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();
    }

    @Test
    void createPrescriptionReturnsCreated() throws Exception {
        when(prescriptionService.createPrescription(any(PrescriptionRequest.class)))
                .thenReturn(response(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].medicineName").value("Synthetic medicine A"));
    }

    @Test
    void getPrescriptionReturnsPrescription() throws Exception {
        UUID id = UUID.randomUUID();
        when(prescriptionService.getPrescription(id)).thenReturn(response(id));

        mockMvc.perform(get("/api/v1/prescriptions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getPatientPrescriptionsReturnsPage() throws Exception {
        when(prescriptionService.getPatientPrescriptions(eq(patientId), any()))
                .thenReturn(new PageImpl<>(List.of(response(UUID.randomUUID())), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/patients/{patientId}/prescriptions", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].patientId").value(patientId.toString()));
    }

    @Test
    void missingPrescriptionReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(prescriptionService.getPrescription(id)).thenThrow(new ResourceNotFoundException("Prescription not found"));

        mockMvc.perform(get("/api/v1/prescriptions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Prescription not found"));
    }

    @Test
    void invalidPrescriptionRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String validRequest() {
        return """
                {
                  "patientId": "%s",
                  "doctorId": "%s",
                  "prescriptionDate": "2026-08-22",
                  "notes": "Synthetic notes",
                  "items": [
                    {
                      "medicineName": "Synthetic medicine A",
                      "dosage": "10mg",
                      "frequency": "Once daily",
                      "duration": "5 days",
                      "instructions": "After food"
                    }
                  ]
                }
                """.formatted(patientId, doctorId);
    }

    private PrescriptionResponse response(UUID id) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        return new PrescriptionResponse(
                id,
                patientId,
                "Asha Rao",
                doctorId,
                "Kiran Shah",
                LocalDate.of(2026, 8, 22),
                "Synthetic notes",
                List.of(new PrescriptionItemResponse(
                        UUID.randomUUID(),
                        "Synthetic medicine A",
                        "10mg",
                        "Once daily",
                        "5 days",
                        "After food"
                )),
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
