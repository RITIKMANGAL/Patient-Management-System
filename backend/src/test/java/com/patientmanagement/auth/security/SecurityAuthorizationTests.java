package com.patientmanagement.auth.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.Role;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.auth.repository.AuthUserRepository;
import com.patientmanagement.auth.repository.RefreshTokenRepository;
import com.patientmanagement.auth.repository.RoleRepository;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.medicalrecord.dto.MedicalRecordRequest;
import com.patientmanagement.medicalrecord.dto.MedicalRecordResponse;
import com.patientmanagement.medicalrecord.service.MedicalRecordService;
import com.patientmanagement.patient.dto.PatientRequest;
import com.patientmanagement.patient.dto.PatientResponse;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.service.PrescriptionService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

    @MockBean
    private AuthUserRepository authUserRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private PatientService patientService;

    @MockBean
    private DoctorService doctorService;

    @MockBean
    private MedicalRecordService medicalRecordService;

    @MockBean
    private PrescriptionService prescriptionService;

    @Test
    void protectedEndpointWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
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
    void receptionistCannotCreatePrescription() throws Exception {
        mockMvc.perform(post("/api/v1/prescriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleName.RECEPTIONIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
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
}
