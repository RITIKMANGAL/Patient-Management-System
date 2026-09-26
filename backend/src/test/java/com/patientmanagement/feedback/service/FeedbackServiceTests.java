package com.patientmanagement.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.consultation.repository.ConsultationRepository;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.feedback.config.FeedbackAccessProperties;
import com.patientmanagement.feedback.dto.FeedbackAccessTokenResponse;
import com.patientmanagement.feedback.dto.FeedbackResponse;
import com.patientmanagement.feedback.dto.FeedbackSubmitRequest;
import com.patientmanagement.feedback.model.Feedback;
import com.patientmanagement.feedback.model.FeedbackAccessToken;
import com.patientmanagement.feedback.repository.FeedbackAccessTokenRepository;
import com.patientmanagement.feedback.repository.FeedbackRepository;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-06T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackAccessTokenRepository accessTokenRepository;

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private CommunicationService communicationService;

    private FeedbackService feedbackService;
    private UUID consultationId;
    private UUID patientId;
    private UUID doctorId;
    private UUID appointmentId;
    private Patient patient;
    private Doctor doctor;
    private Appointment appointment;
    private Consultation consultation;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(
                feedbackRepository,
                accessTokenRepository,
                consultationRepository,
                communicationService,
                new FeedbackAccessProperties(168, "http://localhost:5173/"),
                new SecureRandom(new byte[] {1, 2, 3, 4}),
                FIXED_CLOCK
        );
        consultationId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        appointmentId = UUID.randomUUID();
        patient = patient();
        doctor = doctor();
        appointment = appointment();
        consultation = completedConsultation();
        ReflectionTestUtils.setField(patient, "id", patientId);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
        ReflectionTestUtils.setField(consultation, "id", consultationId);
    }

    @Test
    void createFeedbackAccessTokenPersistsOnlyHashAndReturnsRawTokenOnce() {
        FeedbackAccessToken oldToken = accessToken("old-token", Instant.parse("2026-09-07T00:00:00Z"));
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(accessTokenRepository.findByConsultationIdAndRevokedAtIsNull(consultationId)).thenReturn(List.of(oldToken));

        FeedbackAccessTokenResponse response = feedbackService.createFeedbackAccessToken(consultationId);

        assertThat(response.token()).isNotBlank();
        assertThat(response.feedbackUrl()).isEqualTo("http://localhost:5173/feedback/" + response.token());
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-09-13T10:00:00Z"));
        assertThat(response.toString()).doesNotContain(response.token()).doesNotContain(response.feedbackUrl());
        assertThat(oldToken.getRevokedAt()).isEqualTo(Instant.now(FIXED_CLOCK));

        ArgumentCaptor<FeedbackAccessToken> tokenCaptor = ArgumentCaptor.forClass(FeedbackAccessToken.class);
        verify(accessTokenRepository).save(tokenCaptor.capture());
        FeedbackAccessToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getTokenHash()).hasSize(64);
        assertThat(savedToken.getTokenHash()).isNotEqualTo(response.token());
        assertThat(savedToken.getConsultation()).isSameAs(consultation);
    }

    @Test
    void createFeedbackAccessTokenRejectsIncompleteConsultation() {
        Consultation inProgressConsultation = new Consultation(appointment, Instant.now(FIXED_CLOCK));
        ReflectionTestUtils.setField(inProgressConsultation, "id", consultationId);
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(inProgressConsultation));

        assertThatThrownBy(() -> feedbackService.createFeedbackAccessToken(consultationId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Feedback access can only be created for completed consultations");
    }

    @Test
    void createFeedbackAccessTokenRejectsMissingConsultation() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedbackService.createFeedbackAccessToken(consultationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Consultation not found");
    }

    @Test
    void doctorScopedCreateFeedbackAccessTokenRejectsAnotherDoctorsConsultation() {
        ClinicalAccessService clinicalAccessService = org.mockito.Mockito.mock(ClinicalAccessService.class);
        FeedbackService scopedService = new FeedbackService(
                feedbackRepository,
                accessTokenRepository,
                consultationRepository,
                communicationService,
                new FeedbackAccessProperties(168, "http://localhost:5173/"),
                clinicalAccessService,
                new SecureRandom(new byte[] {1, 2, 3, 4}),
                FIXED_CLOCK
        );
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        doThrow(new ResourceNotFoundException("Consultation not found"))
                .when(clinicalAccessService).requireConsultationAccess(consultation);

        assertThatThrownBy(() -> scopedService.createFeedbackAccessToken(consultationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Consultation not found");
        verifyNoInteractions(accessTokenRepository);
    }

    @Test
    void createFeedbackRequestForCompletedConsultationCreatesTokenAndCommunication() {
        when(accessTokenRepository.findByConsultationIdAndRevokedAtIsNull(consultationId)).thenReturn(List.of());

        feedbackService.createFeedbackRequestForCompletedConsultation(consultation);

        verify(accessTokenRepository).save(any(FeedbackAccessToken.class));
        verify(communicationService).createFeedbackRequest(any(Appointment.class), org.mockito.ArgumentMatchers.startsWith("http://localhost:5173/feedback/"));
    }

    @Test
    void feedbackContextReturnsMinimalVisitContext() {
        FeedbackAccessToken accessToken = accessToken("raw-token", Instant.parse("2026-09-07T00:00:00Z"));
        when(accessTokenRepository.findByTokenHash(feedbackService.hash("raw-token"))).thenReturn(Optional.of(accessToken));
        when(feedbackRepository.existsByConsultationId(consultationId)).thenReturn(false);

        var response = feedbackService.getFeedbackContext("raw-token");

        assertThat(response.doctorName()).isEqualTo("Kiran Shah");
        assertThat(response.appointmentDateTime()).isEqualTo(LocalDateTime.of(2099, 9, 1, 10, 30));
        assertThat(response.submitted()).isFalse();
    }

    @Test
    void invalidExpiredOrRevokedTokensAreRejectedGenerically() {
        assertThatThrownBy(() -> feedbackService.getFeedbackContext(" "))
                .isInstanceOf(FeedbackAccessDeniedException.class);

        FeedbackAccessToken expiredToken = accessToken("expired-token", Instant.parse("2026-09-06T09:59:59Z"));
        when(accessTokenRepository.findByTokenHash(feedbackService.hash("expired-token"))).thenReturn(Optional.of(expiredToken));
        assertThatThrownBy(() -> feedbackService.getFeedbackContext("expired-token"))
                .isInstanceOf(FeedbackAccessDeniedException.class);

        FeedbackAccessToken revokedToken = accessToken("revoked-token", Instant.parse("2026-09-07T00:00:00Z"));
        revokedToken.revoke(Instant.parse("2026-09-06T09:00:00Z"));
        when(accessTokenRepository.findByTokenHash(feedbackService.hash("revoked-token"))).thenReturn(Optional.of(revokedToken));
        assertThatThrownBy(() -> feedbackService.getFeedbackContext("revoked-token"))
                .isInstanceOf(FeedbackAccessDeniedException.class);
    }

    @Test
    void submitFeedbackStoresOneRatingAndRevokesToken() {
        FeedbackAccessToken accessToken = accessToken("raw-token", Instant.parse("2026-09-07T00:00:00Z"));
        when(accessTokenRepository.findByTokenHash(feedbackService.hash("raw-token"))).thenReturn(Optional.of(accessToken));
        when(feedbackRepository.existsByConsultationId(consultationId)).thenReturn(false);
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> {
            Feedback feedback = invocation.getArgument(0);
            ReflectionTestUtils.setField(feedback, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(feedback, "createdAt", Instant.now(FIXED_CLOCK));
            return feedback;
        });

        FeedbackResponse response = feedbackService.submitFeedback(
                "raw-token",
                new FeedbackSubmitRequest(5, "  Helpful visit  ")
        );

        assertThat(response.consultationId()).isEqualTo(consultationId);
        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.doctorId()).isEqualTo(doctorId);
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Helpful visit");
        assertThat(accessToken.getRevokedAt()).isEqualTo(Instant.now(FIXED_CLOCK));
    }

    @Test
    void submitFeedbackRejectsDuplicateSubmission() {
        FeedbackAccessToken accessToken = accessToken("raw-token", Instant.parse("2026-09-07T00:00:00Z"));
        when(accessTokenRepository.findByTokenHash(feedbackService.hash("raw-token"))).thenReturn(Optional.of(accessToken));
        when(feedbackRepository.existsByConsultationId(consultationId)).thenReturn(true);

        assertThatThrownBy(() -> feedbackService.submitFeedback("raw-token", new FeedbackSubmitRequest(4, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Feedback already submitted");
    }

    @Test
    void submitFeedbackMapsDatabaseDuplicateToConflict() {
        FeedbackAccessToken accessToken = accessToken("raw-token", Instant.parse("2026-09-07T00:00:00Z"));
        when(accessTokenRepository.findByTokenHash(feedbackService.hash("raw-token"))).thenReturn(Optional.of(accessToken));
        when(feedbackRepository.existsByConsultationId(consultationId)).thenReturn(false);
        when(feedbackRepository.save(any(Feedback.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> feedbackService.submitFeedback("raw-token", new FeedbackSubmitRequest(4, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Feedback already submitted");
    }

    @Test
    void revokeActiveFeedbackAccessTokenRevokesExistingTokens() {
        FeedbackAccessToken accessToken = accessToken("raw-token", Instant.parse("2026-09-07T00:00:00Z"));
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(accessTokenRepository.findByConsultationIdAndRevokedAtIsNull(consultationId)).thenReturn(List.of(accessToken));

        feedbackService.revokeActiveFeedbackAccessToken(consultationId);

        assertThat(accessToken.getRevokedAt()).isEqualTo(Instant.now(FIXED_CLOCK));
    }

    @Test
    void getFeedbackReturnsPagedStaffView() {
        Feedback feedback = new Feedback(consultation, patient, doctor, 4, null);
        ReflectionTestUtils.setField(feedback, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(feedback, "createdAt", Instant.now(FIXED_CLOCK));
        when(feedbackRepository.findAll(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(feedback), PageRequest.of(0, 20), 1));

        assertThat(feedbackService.getFeedback(PageRequest.of(0, 20)).getContent())
                .extracting(FeedbackResponse::rating)
                .containsExactly(4);
    }

    private FeedbackAccessToken accessToken(String rawToken, Instant expiresAt) {
        return new FeedbackAccessToken(consultation, feedbackService.hash(rawToken), expiresAt);
    }

    private Consultation completedConsultation() {
        Consultation consultation = new Consultation(appointment, Instant.now(FIXED_CLOCK));
        consultation.updateClinicalNotes("Chest pain", null, null, "Musculoskeletal pain", null, null);
        consultation.complete(Instant.now(FIXED_CLOCK));
        appointment.setStatus(AppointmentStatus.COMPLETED);
        return consultation;
    }

    private Appointment appointment() {
        return new Appointment(
                patient,
                doctor,
                LocalDateTime.of(2099, 9, 1, 10, 30),
                "Annual checkup",
                AppointmentStatus.CONFIRMED,
                null
        );
    }

    private Patient patient() {
        return new Patient(
                "Asha",
                "Rao",
                LocalDate.of(1990, 1, 1),
                PatientGender.FEMALE,
                BloodGroup.O_POSITIVE,
                "+15555550100",
                "synthetic.patient@example.com",
                "Synthetic address",
                "Synthetic Contact",
                "+15555550101"
        );
    }

    private Doctor doctor() {
        return new Doctor(
                "Kiran",
                "Shah",
                "Cardiology",
                "LIC-100",
                "+15555550200",
                "synthetic.doctor@example.com",
                "Cardiology"
        );
    }
}
