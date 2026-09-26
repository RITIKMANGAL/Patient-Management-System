package com.patientmanagement.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.patientmanagement.ai.dto.AiConsultationDraft;
import com.patientmanagement.ai.dto.AiConsultationDraftRequest;
import com.patientmanagement.ai.dto.AiConsultationDraftResponse;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;
import com.patientmanagement.ai.service.AiClinicalService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.patientmanagement.appointment.dto.AppointmentRequest;
import com.patientmanagement.appointment.dto.AppointmentResponse;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.service.AppointmentService;
import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.Role;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.auth.repository.AuthUserRepository;
import com.patientmanagement.auth.repository.RefreshTokenRepository;
import com.patientmanagement.auth.repository.RoleRepository;
import com.patientmanagement.communication.dto.CommunicationResponse;
import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationStatus;
import com.patientmanagement.communication.model.CommunicationType;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.consultation.dto.ConsultationResponse;
import com.patientmanagement.consultation.dto.ConsultationUpdateRequest;
import com.patientmanagement.consultation.model.ConsultationStatus;
import com.patientmanagement.consultation.service.ConsultationService;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.feedback.dto.FeedbackAccessTokenResponse;
import com.patientmanagement.feedback.dto.FeedbackContextResponse;
import com.patientmanagement.feedback.dto.FeedbackResponse;
import com.patientmanagement.feedback.dto.FeedbackSubmitRequest;
import com.patientmanagement.feedback.service.FeedbackAccessDeniedException;
import com.patientmanagement.feedback.service.FeedbackService;
import com.patientmanagement.medicalrecord.dto.MedicalRecordRequest;
import com.patientmanagement.medicalrecord.dto.MedicalRecordResponse;
import com.patientmanagement.medicalrecord.service.MedicalRecordService;
import com.patientmanagement.patient.dto.PatientRequest;
import com.patientmanagement.patient.dto.PatientResponse;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.dto.PrescriptionItemResponse;
import com.patientmanagement.prescription.dto.PrescriptionResponse;
import com.patientmanagement.prescription.access.dto.PrescriptionAccessTokenResponse;
import com.patientmanagement.prescription.access.service.PrescriptionAccessDeniedException;
import com.patientmanagement.prescription.access.service.PrescriptionAccessTokenService;
import com.patientmanagement.prescription.service.PrescriptionPdfService;
import com.patientmanagement.prescription.service.PrescriptionService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationTests {

    private static final String TEST_SECRET = "test-only-jwt-secret-for-automated-tests-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthUserRepository authUserRepository;

    @MockitoBean
    private com.patientmanagement.doctor.repository.DoctorRepository doctorRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private PatientService patientService;

    @MockitoBean
    private DoctorService doctorService;

    @MockitoBean
    private MedicalRecordService medicalRecordService;

    @MockitoBean
    private PrescriptionService prescriptionService;

    @MockitoBean
    private PrescriptionPdfService prescriptionPdfService;

    @MockitoBean
    private PrescriptionAccessTokenService prescriptionAccessTokenService;

    @MockitoBean
    private AppointmentService appointmentService;

    @MockitoBean
    private CommunicationService communicationService;

    @MockitoBean
    private ConsultationService consultationService;

    @MockitoBean
    private FeedbackService feedbackService;

    @MockitoBean
    private AiClinicalService aiClinicalService;

    @Test
    void protectedEndpointWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(RoleName.class)
    void anonymousCannotProvisionAnyRole(RoleName role) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffRequest(role)))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verify(authUserRepository, org.mockito.Mockito.never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = RoleName.class, names = {"DOCTOR", "RECEPTIONIST"})
    void nonAdminCannotProvisionAnyRole(RoleName caller) throws Exception {
        String token = tokenFor(caller);
        for (RoleName requested : RoleName.values()) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(staffRequest(requested)))
                    .andExpect(status().isForbidden());
        }
        org.mockito.Mockito.verify(authUserRepository, org.mockito.Mockito.never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(RoleName.class)
    void adminCanProvisionEachRole(RoleName requested) throws Exception {
        when(roleRepository.findByName(requested)).thenReturn(Optional.of(new Role(requested)));
        when(authUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        if (requested == RoleName.DOCTOR) {
            var doctor = new com.patientmanagement.doctor.model.Doctor("Safe", "Doctor", "General", "LIC-123", "+15555550100", null, null);
            when(doctorRepository.findByIdForUpdate(any())).thenReturn(Optional.of(doctor));
        }
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffRequest(requested)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value(requested.name()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private String staffRequest(RoleName role) {
        String doctorField = role == RoleName.DOCTOR ? ",\"doctorId\":\"22222222-2222-2222-2222-222222222222\"" : "";
        return "{\"username\":\"new.staff@example.com\",\"password\":\"SyntheticPass123\",\"firstName\":\"New\",\"lastName\":\"Staff\",\"role\":\""
                + role.name() + "\"" + doctorField + "}";
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken(RoleName.ADMIN))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tamperedJwtReturnsUnauthorized() throws Exception {
        String token = tokenFor(RoleName.ADMIN);
        String[] tokenParts = token.split("\\.");
        String tamperedSignature = (tokenParts[2].startsWith("A") ? "B" : "A") + tokenParts[2].substring(1);
        String tamperedToken = tokenParts[0] + "." + tokenParts[1] + "." + tamperedSignature;

        mockMvc.perform(get("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tamperedToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void wrongSigningAlgorithmJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenWithAlgorithm(JWSAlgorithm.HS384))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void validJwtAllowsProtectedEndpoint() throws Exception {
        when(patientService.getPatients(any()))
                .thenReturn(new PageImpl<>(List.of(patientResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("Asha"));
    }

    @Test
    void wrongRoleReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/medical-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(medicalRecordRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminHasDeletePatientPermission() throws Exception {
        UUID patientId = UUID.randomUUID();
        doNothing().when(patientService).deletePatient(patientId);

        mockMvc.perform(delete("/api/v1/patients/{id}", patientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isNoContent());
    }

    @Test
    void doctorCanCreateMedicalRecord() throws Exception {
        when(medicalRecordService.createMedicalRecord(any(MedicalRecordRequest.class)))
                .thenReturn(medicalRecordResponse());

        mockMvc.perform(post("/api/v1/medical-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(medicalRecordRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diagnosis").value("Synthetic diagnosis"));
    }

    @Test
    void receptionistCanCreatePatient() throws Exception {
        when(patientService.createPatient(any(PatientRequest.class))).thenReturn(patientResponse());

        mockMvc.perform(post("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Asha"));
    }

    @Test
    void prescriptionEndpointWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prescriptionRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminCanCreatePrescription() throws Exception {
        when(prescriptionService.createPrescription(any()))
                .thenReturn(prescriptionResponse());

        mockMvc.perform(post("/api/v1/prescriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prescriptionRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].medicineName").value("Synthetic medicine"));
    }

    @Test
    void doctorCanCreatePrescription() throws Exception {
        when(prescriptionService.createPrescription(any()))
                .thenReturn(prescriptionResponse());

        mockMvc.perform(post("/api/v1/prescriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prescriptionRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].medicineName").value("Synthetic medicine"));
    }

    @Test
    void receptionistCannotCreatePrescription() throws Exception {
        mockMvc.perform(post("/api/v1/prescriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prescriptionRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDownloadPrescriptionPdf() throws Exception {
        UUID prescriptionId = UUID.randomUUID();
        when(prescriptionPdfService.generatePrescriptionPdf(prescriptionId)).thenReturn("%PDF-1.7 synthetic".getBytes());
        when(prescriptionPdfService.filenameFor(prescriptionId)).thenReturn("prescription-" + prescriptionId + ".pdf");

        mockMvc.perform(get("/api/v1/prescriptions/{id}/pdf", prescriptionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf"));
    }

    @Test
    void doctorCanDownloadPrescriptionPdf() throws Exception {
        UUID prescriptionId = UUID.randomUUID();
        when(prescriptionPdfService.generatePrescriptionPdf(prescriptionId)).thenReturn("%PDF-1.7 synthetic".getBytes());
        when(prescriptionPdfService.filenameFor(prescriptionId)).thenReturn("prescription-" + prescriptionId + ".pdf");

        mockMvc.perform(get("/api/v1/prescriptions/{id}/pdf", prescriptionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR))))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf"));
    }

    @Test
    void receptionistCannotDownloadPrescriptionPdf() throws Exception {
        mockMvc.perform(get("/api/v1/prescriptions/{id}/pdf", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void prescriptionPdfWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/prescriptions/{id}/pdf", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminCanCreatePrescriptionAccessToken() throws Exception {
        UUID prescriptionId = UUID.randomUUID();
        when(prescriptionAccessTokenService.createAccessToken(prescriptionId))
                .thenReturn(new PrescriptionAccessTokenResponse("secure-token", Instant.parse("2026-09-13T00:00:00Z")));

        mockMvc.perform(post("/api/v1/prescriptions/{id}/access", prescriptionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("secure-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-13T00:00:00Z"));
    }

    @Test
    void doctorCanCreatePrescriptionAccessToken() throws Exception {
        UUID prescriptionId = UUID.randomUUID();
        when(prescriptionAccessTokenService.createAccessToken(prescriptionId))
                .thenReturn(new PrescriptionAccessTokenResponse("secure-token", Instant.parse("2026-09-13T00:00:00Z")));

        mockMvc.perform(post("/api/v1/prescriptions/{id}/access", prescriptionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("secure-token"));
    }

    @Test
    void receptionistCannotCreatePrescriptionAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/prescriptions/{id}/access", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void prescriptionAccessTokenCreationWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/prescriptions/{id}/access", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminCanRevokePrescriptionAccessToken() throws Exception {
        mockMvc.perform(delete("/api/v1/prescriptions/{id}/access", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isNoContent());
    }

    @Test
    void doctorCanRevokePrescriptionAccessToken() throws Exception {
        mockMvc.perform(delete("/api/v1/prescriptions/{id}/access", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR))))
                .andExpect(status().isNoContent());
    }

    @Test
    void receptionistCannotRevokePrescriptionAccessToken() throws Exception {
        mockMvc.perform(delete("/api/v1/prescriptions/{id}/access", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void prescriptionAccessTokenRevocationWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/prescriptions/{id}/access", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void publicPrescriptionAccessPdfDoesNotRequireJwt() throws Exception {
        when(prescriptionAccessTokenService.generatePrescriptionPdf("secure-token"))
                .thenReturn("%PDF-1.7 synthetic".getBytes());

        mockMvc.perform(get("/api/v1/prescription-access/{token}/pdf", "secure-token"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf"));
    }

    @Test
    void publicPrescriptionAccessPdfRejectsInvalidTokenGenerically() throws Exception {
        when(prescriptionAccessTokenService.generatePrescriptionPdf("bad-token"))
                .thenThrow(new PrescriptionAccessDeniedException());

        mockMvc.perform(get("/api/v1/prescription-access/{token}/pdf", "bad-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Prescription access link is invalid or expired"));
    }

    @Test
    void appointmentEndpointWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void receptionistCanCreateAppointment() throws Exception {
        when(appointmentService.createAppointment(any(AppointmentRequest.class)))
                .thenReturn(appointmentResponse(AppointmentStatus.SCHEDULED));

        mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentRequest(AppointmentStatus.SCHEDULED)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void doctorCannotCreateAppointment() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentRequest(AppointmentStatus.SCHEDULED)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void doctorCanUpdateAppointmentStatus() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentService.updateAppointment(eq(appointmentId), any(AppointmentRequest.class)))
                .thenReturn(appointmentResponse(AppointmentStatus.COMPLETED));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/appointments/{id}",
                                appointmentId
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentRequest(AppointmentStatus.COMPLETED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void adminCanReadCommunicationHistory() throws Exception {
        when(communicationService.getCommunications(any()))
                .thenReturn(new PageImpl<>(List.of(communicationResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/communications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("PRESCRIPTION_AVAILABLE"));
    }

    @Test
    void doctorCannotReadCommunicationHistory() throws Exception {
        mockMvc.perform(get("/api/v1/communications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void receptionistCannotReadCommunicationHistory() throws Exception {
        mockMvc.perform(get("/api/v1/communications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void communicationHistoryWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/communications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminCanStartConsultation() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(consultationService.startConsultation(appointmentId))
                .thenReturn(consultationResponse(appointmentId, ConsultationStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/v1/appointments/{appointmentId}/consultation", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void doctorCanStartConsultation() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(consultationService.startConsultation(appointmentId))
                .thenReturn(consultationResponse(appointmentId, ConsultationStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/v1/appointments/{appointmentId}/consultation", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void receptionistCannotStartConsultation() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/{appointmentId}/consultation", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void consultationEndpointWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/consultations/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void doctorCanUpdateAndCompleteConsultation() throws Exception {
        UUID consultationId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        when(consultationService.updateConsultation(eq(consultationId), any(ConsultationUpdateRequest.class)))
                .thenReturn(consultationResponse(appointmentId, ConsultationStatus.IN_PROGRESS));
        when(consultationService.completeConsultation(consultationId))
                .thenReturn(consultationResponse(appointmentId, ConsultationStatus.COMPLETED));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/consultations/{id}",
                                consultationId
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chiefComplaint": "Chest pain",
                                  "assessment": "Musculoskeletal pain"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/consultations/{id}/complete", consultationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void adminCanCompleteConsultation() throws Exception {
        UUID consultationId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        when(consultationService.completeConsultation(consultationId))
                .thenReturn(consultationResponse(appointmentId, ConsultationStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/consultations/{id}/complete", consultationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void receptionistCannotCompleteConsultation() throws Exception {
        mockMvc.perform(post("/api/v1/consultations/{id}/complete", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void completeConsultationWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/consultations/{id}/complete", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminCanCreateFeedbackAccessToken() throws Exception {
        UUID consultationId = UUID.randomUUID();
        when(feedbackService.createFeedbackAccessToken(consultationId))
                .thenReturn(feedbackAccessTokenResponse());

        mockMvc.perform(post("/api/v1/consultations/{id}/feedback-access", consultationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("feedback-token"))
                .andExpect(jsonPath("$.feedbackUrl").value("http://localhost:5173/feedback/feedback-token"));
    }

    @Test
    void doctorCanCreateFeedbackAccessToken() throws Exception {
        UUID consultationId = UUID.randomUUID();
        when(feedbackService.createFeedbackAccessToken(consultationId))
                .thenReturn(feedbackAccessTokenResponse());

        mockMvc.perform(post("/api/v1/consultations/{id}/feedback-access", consultationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("feedback-token"));
    }

    @Test
    void receptionistCannotCreateFeedbackAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/consultations/{id}/feedback-access", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void feedbackAccessTokenCreationWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/consultations/{id}/feedback-access", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminCanReadFeedbackList() throws Exception {
        when(feedbackService.getFeedback(any()))
                .thenReturn(new PageImpl<>(List.of(feedbackResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/feedback")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(5));
    }

    @Test
    void doctorCanReadFeedbackList() throws Exception {
        when(feedbackService.getFeedback(any()))
                .thenReturn(new PageImpl<>(List.of(feedbackResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/feedback")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].doctorName").value("Kiran Shah"));
    }

    @Test
    void receptionistCannotReadFeedbackList() throws Exception {
        mockMvc.perform(get("/api/v1/feedback")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void publicFeedbackContextDoesNotRequireJwt() throws Exception {
        when(feedbackService.getFeedbackContext("feedback-token"))
                .thenReturn(new FeedbackContextResponse("Kiran Shah", LocalDateTime.of(2026, 9, 1, 10, 0), false));

        mockMvc.perform(get("/api/v1/feedback-access/{token}", "feedback-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorName").value("Kiran Shah"))
                .andExpect(jsonPath("$.submitted").value(false));
    }

    @Test
    void publicFeedbackSubmitDoesNotRequireJwt() throws Exception {
        when(feedbackService.submitFeedback(eq("feedback-token"), any(FeedbackSubmitRequest.class)))
                .thenReturn(feedbackResponse());

        mockMvc.perform(post("/api/v1/feedback-access/{token}", "feedback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "comment": "Helpful visit"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void publicFeedbackRejectsInvalidTokenGenerically() throws Exception {
        when(feedbackService.getFeedbackContext("bad-token"))
                .thenThrow(new FeedbackAccessDeniedException());

        mockMvc.perform(get("/api/v1/feedback-access/{token}", "bad-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Feedback link is invalid or expired"));
    }

    @Test
    void receptionistCannotUpdateConsultation() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/consultations/{id}",
                                UUID.randomUUID()
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminCanGenerateAiConsultationDraft() throws Exception {
        UUID consultationId = UUID.randomUUID();
        when(aiClinicalService.generateConsultationDraft(eq(consultationId), any(AiConsultationDraftRequest.class)))
                .thenReturn(aiDraftResponse());

        mockMvc.perform(post("/api/v1/consultations/{id}/ai/draft", consultationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roughNotes": "Patient has fever for 3 days."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.chiefComplaint").value("Fever"));
    }

    @Test
    void doctorCanGenerateAiConsultationDraft() throws Exception {
        UUID consultationId = UUID.randomUUID();
        when(aiClinicalService.generateConsultationDraft(eq(consultationId), any(AiConsultationDraftRequest.class)))
                .thenReturn(aiDraftResponse());

        mockMvc.perform(post("/api/v1/consultations/{id}/ai/draft", consultationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roughNotes": "Patient has fever for 3 days."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").value("AI-generated draft - review before saving."));
    }

    @Test
    void receptionistCannotGenerateAiConsultationDraft() throws Exception {
        mockMvc.perform(post("/api/v1/consultations/{id}/ai/draft", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roughNotes": "Patient notes"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void aiConsultationDraftWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/consultations/{id}/ai/draft", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roughNotes": "Patient notes"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminCanGenerateAiPatientSummary() throws Exception {
        UUID patientId = UUID.randomUUID();
        when(aiClinicalService.summarizePatientHistory(patientId)).thenReturn(aiSummaryResponse());

        mockMvc.perform(get("/api/v1/patients/{id}/ai/summary", patientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Summary is based only on documented records."));
    }

    @Test
    void doctorCanGenerateAiPatientSummary() throws Exception {
        UUID patientId = UUID.randomUUID();
        when(aiClinicalService.summarizePatientHistory(patientId)).thenReturn(aiSummaryResponse());

        mockMvc.perform(get("/api/v1/patients/{id}/ai/summary", patientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.DOCTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").value("AI-generated summary. Verify against the patient's records."));
    }

    @Test
    void receptionistCannotGenerateAiPatientSummary() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/ai/summary", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void aiPatientSummaryWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/ai/summary", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void defaultPaginationIsAppliedToListEndpoint() throws Exception {
        when(patientService.getPatients(any()))
                .thenReturn(new PageImpl<>(List.of(patientResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(patientService).getPatients(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void requestedPaginationAndSortingAreAppliedToListEndpoint() throws Exception {
        when(patientService.getPatients(any()))
                .thenReturn(new PageImpl<>(List.of(patientResponse()), PageRequest.of(2, 15), 1));

        mockMvc.perform(get("/api/v1/patients")
                        .param("page", "2")
                        .param("size", "15")
                        .param("sort", "lastName,desc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(patientService).getPatients(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(15);
        assertThat(pageable.getSort().getOrderFor("lastName")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("lastName").getDirection().name()).isEqualTo("DESC");
    }

    @Test
    void excessivePageSizeIsCappedForListEndpoint() throws Exception {
        when(patientService.getPatients(any()))
                .thenReturn(new PageImpl<>(List.of(patientResponse()), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/patients")
                        .param("size", "1000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(patientService).getPatients(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void unsupportedSortFieldReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .param("sort", "passwordHash,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.ADMIN))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported sort field: passwordHash"));
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Frame-Options", "DENY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Referrer-Policy", "no-referrer"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Permissions-Policy", "camera=(), geolocation=(), microphone=(), payment=(), usb=()"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"));
    }

    @Test
    void corsAllowsConfiguredFrontendOriginWithoutCookieCredentials() throws Exception {
        mockMvc.perform(options("/api/v1/patients")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    private String tokenFor(RoleName roleName) {
        AuthUser user = user(roleName);
        when(authUserRepository.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
        return jwtService.createAccessToken(user);
    }

    private String expiredToken(RoleName roleName) throws Exception {
        Instant issuedAt = Instant.parse("2026-08-26T00:00:00Z");
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", roleName.name().toLowerCase() + ".expired@example.com")
                .claim("roles", List.of(roleName.name()))
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.minusSeconds(60)))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
        return signedJWT.serialize();
    }

    private String tokenWithAlgorithm(JWSAlgorithm algorithm) throws Exception {
        Instant issuedAt = Instant.parse("2026-08-26T00:00:00Z");
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "wrong.algorithm@example.com")
                .claim("roles", List.of(RoleName.ADMIN.name()))
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(900)))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(algorithm), claims);
        signedJWT.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
        return signedJWT.serialize();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private AuthUser user(RoleName roleName) {
        String username = roleName.name().toLowerCase() + ".user@example.com";
        AuthUser user = new AuthUser(username, "$2a$10$syntheticHashForSecurityTests", "Security", "User", true);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.addRole(new Role(roleName));
        return user;
    }

    private String patientRequest() {
        return """
                {
                  "firstName": "Asha",
                  "lastName": "Rao",
                  "dateOfBirth": "1990-01-01",
                  "gender": "FEMALE",
                  "bloodGroup": "O_POSITIVE",
                  "phone": "+15555550100",
                  "email": "synthetic.patient@example.com"
                }
                """;
    }

    private PatientResponse patientResponse() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        return new PatientResponse(
                UUID.randomUUID(),
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

    private String medicalRecordRequest() {
        return """
                {
                  "patientId": "%s",
                  "doctorId": "%s",
                  "diagnosis": "Synthetic diagnosis",
                  "recordDate": "2026-08-26"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private MedicalRecordResponse medicalRecordResponse() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        return new MedicalRecordResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Asha Rao",
                UUID.randomUUID(),
                "Kiran Shah",
                "Synthetic diagnosis",
                "Synthetic symptoms",
                "Synthetic notes",
                LocalDate.of(2026, 8, 26),
                now,
                now
        );
    }

    private String prescriptionRequest() {
        return """
                {
                  "patientId": "%s",
                  "doctorId": "%s",
                  "prescriptionDate": "2026-08-26",
                  "items": [
                    {
                      "medicineName": "Synthetic medicine",
                      "dosage": "10mg",
                      "frequency": "Once daily",
                      "duration": "5 days"
                    }
                  ]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private PrescriptionResponse prescriptionResponse() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        return new PrescriptionResponse(
                UUID.randomUUID(),
                patientId,
                "Asha Rao",
                doctorId,
                "Kiran Shah",
                LocalDate.of(2026, 8, 26),
                null,
                List.of(new PrescriptionItemResponse(
                        UUID.randomUUID(),
                        "Synthetic medicine",
                        "10mg",
                        "Once daily",
                        "5 days",
                        null
                )),
                now,
                now
        );
    }

    private String appointmentRequest(AppointmentStatus status) {
        return """
                {
                  "patientId": "%s",
                  "doctorId": "%s",
                  "appointmentDateTime": "2026-09-01T10:00:00",
                  "reason": "Annual checkup",
                  "status": "%s",
                  "notes": "Bring reports"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), status);
    }

    private AppointmentResponse appointmentResponse(AppointmentStatus status) {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        return new AppointmentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Asha Rao",
                UUID.randomUUID(),
                "Kiran Shah",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "Annual checkup",
                status,
                "Bring reports",
                now,
                now
        );
    }

    private CommunicationResponse communicationResponse() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        return new CommunicationResponse(
                UUID.randomUUID(),
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

    private ConsultationResponse consultationResponse(UUID appointmentId, ConsultationStatus status) {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        return new ConsultationResponse(
                UUID.randomUUID(),
                appointmentId,
                UUID.randomUUID(),
                "Asha Rao",
                UUID.randomUUID(),
                "Kiran Shah",
                LocalDateTime.of(2026, 9, 1, 10, 0),
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

    private AiConsultationDraftResponse aiDraftResponse() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
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
                now,
                "AI-generated draft - review before saving."
        );
    }

    private AiPatientHistorySummaryResponse aiSummaryResponse() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        return new AiPatientHistorySummaryResponse(
                "Summary is based only on documented records.",
                List.of("Consultation on 2026-09-03"),
                List.of("Medical record on 2026-09-03"),
                List.of("Prescription on 2026-09-03"),
                List.of("Follow-up: Review in one week"),
                "mock",
                "test-model",
                now,
                "AI-generated summary. Verify against the patient's records."
        );
    }

    private FeedbackAccessTokenResponse feedbackAccessTokenResponse() {
        return new FeedbackAccessTokenResponse(
                "feedback-token",
                "http://localhost:5173/feedback/feedback-token",
                Instant.parse("2026-09-13T00:00:00Z")
        );
    }

    private FeedbackResponse feedbackResponse() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        return new FeedbackResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                patientId,
                "Asha Rao",
                doctorId,
                "Kiran Shah",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                5,
                "Helpful visit",
                now
        );
    }
}
