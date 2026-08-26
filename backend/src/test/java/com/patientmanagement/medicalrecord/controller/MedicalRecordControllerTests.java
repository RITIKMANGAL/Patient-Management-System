package com.patientmanagement.medicalrecord.controller;

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
import com.patientmanagement.medicalrecord.dto.MedicalRecordRequest;
import com.patientmanagement.medicalrecord.dto.MedicalRecordResponse;
import com.patientmanagement.medicalrecord.service.MedicalRecordService;
import java.time.Instant;
import java.time.LocalDate;
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

class MedicalRecordControllerTests {

    private MedicalRecordService medicalRecordService;
    private MockMvc mockMvc;
    private UUID patientId;
    private UUID doctorId;

    @BeforeEach
    void setUp() {
        medicalRecordService = org.mockito.Mockito.mock(MedicalRecordService.class);
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(new MedicalRecordController(medicalRecordService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();
    }

    @Test
    void createMedicalRecordReturnsCreated() throws Exception {
        when(medicalRecordService.createMedicalRecord(any(MedicalRecordRequest.class)))
                .thenReturn(response(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/medical-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diagnosis").value("Synthetic diagnosis"));
    }

    @Test
    void getMedicalRecordReturnsRecord() throws Exception {
        UUID id = UUID.randomUUID();
        when(medicalRecordService.getMedicalRecord(id)).thenReturn(response(id));

        mockMvc.perform(get("/api/v1/medical-records/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getPatientMedicalRecordsReturnsPage() throws Exception {
        when(medicalRecordService.getPatientMedicalRecords(eq(patientId), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(response(UUID.randomUUID())), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/patients/{patientId}/medical-records", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].patientId").value(patientId.toString()));
    }

    @Test
    void missingMedicalRecordReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(medicalRecordService.getMedicalRecord(id))
                .thenThrow(new ResourceNotFoundException("Medical record not found"));

        mockMvc.perform(get("/api/v1/medical-records/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Medical record not found"));
    }

    @Test
    void invalidMedicalRecordRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/medical-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String validRequest() {
        return """
                {
                  "patientId": "%s",
                  "doctorId": "%s",
                  "diagnosis": "Synthetic diagnosis",
                  "symptoms": "Synthetic symptoms",
                  "notes": "Synthetic notes",
                  "recordDate": "2026-08-22"
                }
                """.formatted(patientId, doctorId);
    }

    private MedicalRecordResponse response(UUID id) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        return new MedicalRecordResponse(
                id,
                patientId,
                "Asha Rao",
                doctorId,
                "Kiran Shah",
                "Synthetic diagnosis",
                "Synthetic symptoms",
                "Synthetic notes",
                LocalDate.of(2026, 8, 22),
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
