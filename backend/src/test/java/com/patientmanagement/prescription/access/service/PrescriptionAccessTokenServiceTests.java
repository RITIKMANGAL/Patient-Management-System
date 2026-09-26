package com.patientmanagement.prescription.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.prescription.access.config.PrescriptionAccessProperties;
import com.patientmanagement.prescription.access.dto.PrescriptionAccessTokenResponse;
import com.patientmanagement.prescription.access.dto.PrescriptionAccessStatusResponse;
import com.patientmanagement.prescription.access.model.PrescriptionAccessToken;
import com.patientmanagement.prescription.access.repository.PrescriptionAccessTokenRepository;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.model.PrescriptionItem;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import com.patientmanagement.prescription.service.PrescriptionPdfService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PrescriptionAccessTokenServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @Mock
    private PrescriptionAccessTokenRepository accessTokenRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private PrescriptionPdfService prescriptionPdfService;

    private PrescriptionAccessTokenService service;
    private UUID prescriptionId;
    private Prescription prescription;

    @BeforeEach
    void setUp() {
        prescriptionId = UUID.randomUUID();
        prescription = prescription();
        ReflectionTestUtils.setField(prescription, "id", prescriptionId);
        service = new PrescriptionAccessTokenService(
                accessTokenRepository,
                prescriptionRepository,
                prescriptionPdfService,
                new PrescriptionAccessProperties(168),
                new SecureRandom(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createAccessTokenReturnsHighEntropyUrlSafeToken() {
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(accessTokenRepository.findByPrescriptionIdAndRevokedAtIsNull(prescriptionId)).thenReturn(List.of());

        PrescriptionAccessTokenResponse response = service.createAccessToken(prescriptionId);

        assertThat(response.token()).isNotBlank();
        assertThat(response.token()).hasSize(64);
        assertThat(response.token()).matches("[A-Za-z0-9_-]+");
        assertThat(response.token()).doesNotContain(prescriptionId.toString());
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(168 * 3600));
    }

    @Test
    void generatedTokensAreRandomAcrossCalls() {
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(accessTokenRepository.findByPrescriptionIdAndRevokedAtIsNull(prescriptionId)).thenReturn(List.of());
        Set<String> tokens = new HashSet<>();

        for (int index = 0; index < 25; index += 1) {
            tokens.add(service.createAccessToken(prescriptionId).token());
        }

        assertThat(tokens).hasSize(25);
    }

    @Test
    void rawTokenIsNotPersistedAndOnlyHashIsStored() {
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(accessTokenRepository.findByPrescriptionIdAndRevokedAtIsNull(prescriptionId)).thenReturn(List.of());
        ArgumentCaptor<PrescriptionAccessToken> captor = ArgumentCaptor.forClass(PrescriptionAccessToken.class);

        PrescriptionAccessTokenResponse response = service.createAccessToken(prescriptionId);

        verify(accessTokenRepository).save(captor.capture());
        PrescriptionAccessToken stored = captor.getValue();
        assertThat(stored.getTokenHash()).isNotEqualTo(response.token());
        assertThat(stored.getTokenHash()).hasSize(64);
        assertThat(stored.getTokenHash()).matches("[a-f0-9]{64}");
    }

    @Test
    void tokenResponseToStringDoesNotExposeRawToken() {
        PrescriptionAccessTokenResponse response = new PrescriptionAccessTokenResponse(
                "patient-token-should-not-appear",
                NOW.plusSeconds(3600)
        );

        assertThat(response.toString())
                .contains("token=<redacted>")
                .doesNotContain("patient-token-should-not-appear");
    }

    @Test
    void newTokenRevokesPreviousUnrevokedTokenForPrescription() {
        PrescriptionAccessToken oldToken = new PrescriptionAccessToken(
                prescription,
                service.hash("old-token"),
                NOW.plusSeconds(3600)
        );
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(accessTokenRepository.findByPrescriptionIdAndRevokedAtIsNull(prescriptionId)).thenReturn(List.of(oldToken));

        service.createAccessToken(prescriptionId);

        assertThat(oldToken.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void activeAccessStatusReturnsExpiryWithoutExposingToken() {
        PrescriptionAccessToken activeToken = new PrescriptionAccessToken(
                prescription,
                service.hash("active-token"),
                NOW.plusSeconds(3600)
        );
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(accessTokenRepository.findByPrescriptionIdAndRevokedAtIsNull(prescriptionId))
                .thenReturn(List.of(activeToken));

        PrescriptionAccessStatusResponse response = service.getActiveAccessStatus(prescriptionId);

        assertThat(response.active()).isTrue();
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void expiredAccessStatusIsReportedAsInactive() {
        PrescriptionAccessToken expiredToken = new PrescriptionAccessToken(
                prescription,
                service.hash("expired-token"),
                NOW.minusSeconds(1)
        );
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(accessTokenRepository.findByPrescriptionIdAndRevokedAtIsNull(prescriptionId))
                .thenReturn(List.of(expiredToken));

        PrescriptionAccessStatusResponse response = service.getActiveAccessStatus(prescriptionId);

        assertThat(response.active()).isFalse();
        assertThat(response.expiresAt()).isNull();
    }

    @Test
    void createAccessTokenFailsWhenPrescriptionDoesNotExist() {
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAccessToken(prescriptionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Prescription not found");
        verify(accessTokenRepository, never()).save(any());
    }

    @Test
    void validTokenGeneratesPdfForAssociatedPrescriptionOnly() {
        String rawToken = "valid-token";
        byte[] pdf = "%PDF-1.7".getBytes();
        PrescriptionAccessToken accessToken = new PrescriptionAccessToken(
                prescription,
                service.hash(rawToken),
                NOW.plusSeconds(3600)
        );
        when(accessTokenRepository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(accessToken));
        when(prescriptionPdfService.generatePrescriptionPdf(prescriptionId)).thenReturn(pdf);

        byte[] result = service.generatePrescriptionPdf(rawToken);

        assertThat(result).isEqualTo(pdf);
        verify(prescriptionPdfService).generatePrescriptionPdf(prescriptionId);
    }

    @Test
    void invalidTokenFailsWithGenericAccessError() {
        when(accessTokenRepository.findByTokenHash(service.hash("missing-token"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generatePrescriptionPdf("missing-token"))
                .isInstanceOf(PrescriptionAccessDeniedException.class)
                .hasMessage("Prescription access link is invalid or expired");
        verify(prescriptionPdfService, never()).generatePrescriptionPdf(any());
    }

    @Test
    void expiredTokenFailsWithGenericAccessError() {
        String rawToken = "expired-token";
        PrescriptionAccessToken accessToken = new PrescriptionAccessToken(
                prescription,
                service.hash(rawToken),
                NOW.minusSeconds(1)
        );
        when(accessTokenRepository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(accessToken));

        assertThatThrownBy(() -> service.generatePrescriptionPdf(rawToken))
                .isInstanceOf(PrescriptionAccessDeniedException.class)
                .hasMessage("Prescription access link is invalid or expired");
        verify(prescriptionPdfService, never()).generatePrescriptionPdf(any());
    }

    @Test
    void revokedTokenFailsWithGenericAccessError() {
        String rawToken = "revoked-token";
        PrescriptionAccessToken accessToken = new PrescriptionAccessToken(
                prescription,
                service.hash(rawToken),
                NOW.plusSeconds(3600)
        );
        accessToken.revoke(NOW.minusSeconds(60));
        when(accessTokenRepository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(accessToken));

        assertThatThrownBy(() -> service.generatePrescriptionPdf(rawToken))
                .isInstanceOf(PrescriptionAccessDeniedException.class)
                .hasMessage("Prescription access link is invalid or expired");
        verify(prescriptionPdfService, never()).generatePrescriptionPdf(any());
    }

    @Test
    void revokeActiveAccessTokenRevokesUnrevokedTokens() {
        PrescriptionAccessToken accessToken = new PrescriptionAccessToken(
                prescription,
                service.hash("active-token"),
                NOW.plusSeconds(3600)
        );
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(accessTokenRepository.findByPrescriptionIdAndRevokedAtIsNull(prescriptionId)).thenReturn(List.of(accessToken));

        service.revokeActiveAccessToken(prescriptionId);

        assertThat(accessToken.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void doctorScopedCreateAccessTokenRejectsAnotherDoctorsPrescription() {
        ClinicalAccessService clinicalAccessService = org.mockito.Mockito.mock(ClinicalAccessService.class);
        PrescriptionAccessTokenService scopedService = new PrescriptionAccessTokenService(
                accessTokenRepository,
                prescriptionRepository,
                prescriptionPdfService,
                new PrescriptionAccessProperties(168),
                clinicalAccessService,
                new SecureRandom(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        doThrow(new ResourceNotFoundException("Prescription not found"))
                .when(clinicalAccessService).requirePrescriptionAccess(prescription);

        assertThatThrownBy(() -> scopedService.createAccessToken(prescriptionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Prescription not found");
        verify(accessTokenRepository, never()).save(any());
    }

    @Test
    void revokedTokenCannotBeReusedAfterRevocation() {
        String rawToken = "active-token";
        PrescriptionAccessToken accessToken = new PrescriptionAccessToken(
                prescription,
                service.hash(rawToken),
                NOW.plusSeconds(3600)
        );
        accessToken.revoke(NOW);
        when(accessTokenRepository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(accessToken));

        assertThatThrownBy(() -> service.generatePrescriptionPdf(rawToken))
                .isInstanceOf(PrescriptionAccessDeniedException.class);
    }

    private Prescription prescription() {
        Prescription value = new Prescription(
                patient(),
                doctor(),
                LocalDate.of(2026, 9, 6),
                "Use exactly as directed"
        );
        value.addItem(new PrescriptionItem("Synthetic medicine", "10mg", "Daily", "5 days", null));
        return value;
    }

    private Patient patient() {
        Patient patient = new Patient(
                "Asha",
                "Rao",
                LocalDate.of(1990, 1, 1),
                PatientGender.FEMALE,
                BloodGroup.O_POSITIVE,
                "+15555550100",
                "asha.rao@example.com",
                "Synthetic address",
                "Synthetic Contact",
                "+15555550101"
        );
        ReflectionTestUtils.setField(patient, "id", UUID.randomUUID());
        return patient;
    }

    private Doctor doctor() {
        Doctor doctor = new Doctor(
                "Kiran",
                "Shah",
                "Cardiology",
                "LIC-100",
                "+15555550200",
                "kiran.shah@example.com",
                "Cardiology"
        );
        ReflectionTestUtils.setField(doctor, "id", UUID.randomUUID());
        return doctor;
    }
}
