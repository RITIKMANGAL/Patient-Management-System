package com.patientmanagement.patient.controller;

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
import com.patientmanagement.common.exception.GlobalExceptionHandler;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.patient.dto.PatientRequest;
import com.patientmanagement.patient.dto.PatientResponse;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.service.PatientService;
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

class PatientControllerTests {

    private PatientService patientService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        patientService = org.mockito.Mockito.mock(PatientService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PatientController(patientService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();
    }

    @Test
    void createPatientReturnsCreated() throws Exception {
        when(patientService.createPatient(any(PatientRequest.class))).thenReturn(response(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Asha"));
    }

    @Test
    void getPatientReturnsPatient() throws Exception {
        UUID id = UUID.randomUUID();
        when(patientService.getPatient(id)).thenReturn(response(id));

        mockMvc.perform(get("/api/v1/patients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void listPatientsReturnsPage() throws Exception {
        when(patientService.getPatients(any()))
                .thenReturn(new PageImpl<>(java.util.List.of(response(UUID.randomUUID())), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("Asha"));
    }

    @Test
    void listPatientsRejectsUnsupportedSortField() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported sort field: passwordHash"));
    }

    @Test
    void updatePatientReturnsUpdatedPatient() throws Exception {
        UUID id = UUID.randomUUID();
        when(patientService.updatePatient(eq(id), any(PatientRequest.class))).thenReturn(response(id));

        mockMvc.perform(put("/api/v1/patients/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deletePatientReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(patientService).deletePatient(id);

        mockMvc.perform(delete("/api/v1/patients/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void missingPatientReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(patientService.getPatient(id)).thenThrow(new ResourceNotFoundException("Patient not found"));

        mockMvc.perform(get("/api/v1/patients/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Patient not found"));
    }

    @Test
    void invalidPatientRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void numericPatientNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("Asha", "12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("firstName")));
    }

    private String validRequest() {
        return """
                {
                  "firstName": "Asha",
                  "lastName": "Rao",
                  "dateOfBirth": "1990-01-01",
                  "gender": "FEMALE",
                  "bloodGroup": "O_POSITIVE",
                  "phone": "+15555550100",
                  "email": "synthetic.patient@example.com",
                  "address": "Synthetic address",
                  "emergencyContactName": "Synthetic Contact",
                  "emergencyContactPhone": "+15555550101"
                }
                """;
    }

    private PatientResponse response(UUID id) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        return new PatientResponse(
                id,
                "Asha",
                "Rao",
                LocalDate.of(1990, 1, 1),
                PatientGender.FEMALE,
                BloodGroup.O_POSITIVE,
                "+15555550100",
                "synthetic.patient@example.com",
                "Synthetic address",
                "Synthetic Contact",
                "+15555550101",
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
