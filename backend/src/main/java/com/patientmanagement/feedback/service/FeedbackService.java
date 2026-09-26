package com.patientmanagement.feedback.service;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.consultation.model.ConsultationStatus;
import com.patientmanagement.consultation.repository.ConsultationRepository;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.feedback.config.FeedbackAccessProperties;
import com.patientmanagement.feedback.dto.FeedbackAccessTokenResponse;
import com.patientmanagement.feedback.dto.FeedbackContextResponse;
import com.patientmanagement.feedback.dto.FeedbackResponse;
import com.patientmanagement.feedback.dto.FeedbackSubmitRequest;
import com.patientmanagement.feedback.model.Feedback;
import com.patientmanagement.feedback.model.FeedbackAccessToken;
import com.patientmanagement.feedback.repository.FeedbackAccessTokenRepository;
import com.patientmanagement.feedback.repository.FeedbackRepository;
import com.patientmanagement.patient.model.Patient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private static final int TOKEN_BYTES = 48;

    private final FeedbackRepository feedbackRepository;
    private final FeedbackAccessTokenRepository accessTokenRepository;
    private final ConsultationRepository consultationRepository;
    private final CommunicationService communicationService;
    private final FeedbackAccessProperties properties;
    private final ClinicalAccessService clinicalAccessService;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public FeedbackService(
            FeedbackRepository feedbackRepository,
            FeedbackAccessTokenRepository accessTokenRepository,
            ConsultationRepository consultationRepository,
            CommunicationService communicationService,
            FeedbackAccessProperties properties,
            ClinicalAccessService clinicalAccessService
    ) {
        this(
                feedbackRepository,
                accessTokenRepository,
                consultationRepository,
                communicationService,
                properties,
                clinicalAccessService,
                new SecureRandom(),
                Clock.systemUTC()
        );
    }

    FeedbackService(
            FeedbackRepository feedbackRepository,
            FeedbackAccessTokenRepository accessTokenRepository,
            ConsultationRepository consultationRepository,
            CommunicationService communicationService,
            FeedbackAccessProperties properties,
            ClinicalAccessService clinicalAccessService,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.feedbackRepository = feedbackRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.consultationRepository = consultationRepository;
        this.communicationService = communicationService;
        this.properties = properties;
        this.clinicalAccessService = clinicalAccessService;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    FeedbackService(
            FeedbackRepository feedbackRepository,
            FeedbackAccessTokenRepository accessTokenRepository,
            ConsultationRepository consultationRepository,
            CommunicationService communicationService,
            FeedbackAccessProperties properties,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this(
                feedbackRepository,
                accessTokenRepository,
                consultationRepository,
                communicationService,
                properties,
                null,
                secureRandom,
                clock
        );
    }

    @Transactional
    public FeedbackAccessTokenResponse createFeedbackAccessToken(UUID consultationId) {
        Consultation consultation = findCompletedConsultation(consultationId);
        return createAccessTokenForCompletedConsultation(consultation);
    }

    @Transactional
    public void revokeActiveFeedbackAccessToken(UUID consultationId) {
        findConsultation(consultationId);
        revokeUnrevokedTokens(consultationId, Instant.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createFeedbackRequestForCompletedConsultation(Consultation consultation) {
        if (consultation == null || consultation.getId() == null) {
            throw new InvalidRequestException("Consultation is required");
        }
        if (consultation.getStatus() != ConsultationStatus.COMPLETED) {
            throw new InvalidRequestException("Feedback access can only be created for completed consultations");
        }
        FeedbackAccessTokenResponse tokenResponse = createAccessTokenForCompletedConsultation(consultation);
        communicationService.createFeedbackRequest(consultation.getAppointment(), tokenResponse.feedbackUrl());
    }

    @Transactional(readOnly = true)
    public FeedbackContextResponse getFeedbackContext(String rawToken) {
        FeedbackAccessToken accessToken = validate(rawToken);
        Consultation consultation = accessToken.getConsultation();
        Appointment appointment = consultation.getAppointment();
        Doctor doctor = appointment.getDoctor();
        return new FeedbackContextResponse(
                doctor.getFirstName() + " " + doctor.getLastName(),
                appointment.getAppointmentDateTime(),
                feedbackRepository.existsByConsultationId(consultation.getId())
        );
    }

    @Transactional
    public FeedbackResponse submitFeedback(String rawToken, FeedbackSubmitRequest request) {
        FeedbackAccessToken accessToken = validate(rawToken);
        Consultation consultation = accessToken.getConsultation();
        if (feedbackRepository.existsByConsultationId(consultation.getId())) {
            throw new DuplicateResourceException("Feedback already submitted");
        }

        Appointment appointment = consultation.getAppointment();
        Patient patient = appointment.getPatient();
        Doctor doctor = appointment.getDoctor();
        Feedback feedback = new Feedback(
                consultation,
                patient,
                doctor,
                request.rating(),
                nullableTrim(request.comment())
        );

        try {
            Feedback savedFeedback = feedbackRepository.save(feedback);
            accessToken.revoke(Instant.now(clock));
            return toResponse(savedFeedback);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("Feedback already submitted");
        }
    }

    @Transactional(readOnly = true)
    public Page<FeedbackResponse> getFeedback(Pageable pageable) {
        if (clinicalAccessService != null && clinicalAccessService.scopedDoctorId().isPresent()) {
            return feedbackRepository.findByDoctorId(clinicalAccessService.currentDoctorId(), pageable).map(this::toResponse);
        }
        return feedbackRepository.findAll(pageable).map(this::toResponse);
    }

    String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private Consultation findCompletedConsultation(UUID consultationId) {
        Consultation consultation = findConsultation(consultationId);
        if (consultation.getStatus() != ConsultationStatus.COMPLETED) {
            throw new InvalidRequestException("Feedback access can only be created for completed consultations");
        }
        return consultation;
    }

    private FeedbackAccessTokenResponse createAccessTokenForCompletedConsultation(Consultation consultation) {
        Instant now = Instant.now(clock);

        revokeUnrevokedTokens(consultation.getId(), now);
        accessTokenRepository.flush();

        String rawToken = randomToken();
        Instant expiresAt = now.plusSeconds(properties.tokenExpirationHours() * 3600);
        accessTokenRepository.save(new FeedbackAccessToken(consultation, hash(rawToken), expiresAt));
        return new FeedbackAccessTokenResponse(rawToken, feedbackUrl(rawToken), expiresAt);
    }

    private Consultation findConsultation(UUID consultationId) {
        if (consultationId == null) {
            throw new InvalidRequestException("Consultation is required");
        }
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found"));
        if (clinicalAccessService != null) {
            clinicalAccessService.requireConsultationAccess(consultation);
        }
        return consultation;
    }

    private FeedbackAccessToken validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new FeedbackAccessDeniedException();
        }

        FeedbackAccessToken accessToken = accessTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(FeedbackAccessDeniedException::new);

        Instant now = Instant.now(clock);
        if (accessToken.getRevokedAt() != null || !accessToken.getExpiresAt().isAfter(now)) {
            throw new FeedbackAccessDeniedException();
        }

        return accessToken;
    }

    private void revokeUnrevokedTokens(UUID consultationId, Instant revokedAt) {
        accessTokenRepository.findByConsultationIdAndRevokedAtIsNull(consultationId)
                .forEach(accessToken -> accessToken.revoke(revokedAt));
    }

    private String feedbackUrl(String rawToken) {
        return properties.publicBaseUrl() + "/feedback/" + rawToken;
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private FeedbackResponse toResponse(Feedback feedback) {
        Consultation consultation = feedback.getConsultation();
        Appointment appointment = consultation.getAppointment();
        Patient patient = feedback.getPatient();
        Doctor doctor = feedback.getDoctor();
        return new FeedbackResponse(
                feedback.getId(),
                consultation.getId(),
                patient.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctor.getId(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                appointment.getAppointmentDateTime(),
                feedback.getRating(),
                feedback.getComment(),
                feedback.getCreatedAt()
        );
    }

    private String nullableTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
