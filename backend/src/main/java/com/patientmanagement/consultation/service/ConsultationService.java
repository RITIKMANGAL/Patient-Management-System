package com.patientmanagement.consultation.service;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.consultation.dto.ConsultationResponse;
import com.patientmanagement.consultation.dto.ConsultationUpdateRequest;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.consultation.model.ConsultationStatus;
import com.patientmanagement.consultation.repository.ConsultationRepository;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.feedback.service.FeedbackService;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.service.PatientService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultationService {

    private static final Set<AppointmentStatus> CONSULTATION_ELIGIBLE_APPOINTMENT_STATUSES =
            EnumSet.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);

    private final ConsultationRepository consultationRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientService patientService;
    private final CommunicationService communicationService;
    private final FeedbackService feedbackService;
    private final ClinicalAccessService clinicalAccessService;
    private final Clock clock;

    public ConsultationService(
            ConsultationRepository consultationRepository,
            AppointmentRepository appointmentRepository,
            PatientService patientService,
            CommunicationService communicationService,
            FeedbackService feedbackService,
            ClinicalAccessService clinicalAccessService
    ) {
        this(
                consultationRepository,
                appointmentRepository,
                patientService,
                communicationService,
                feedbackService,
                clinicalAccessService,
                Clock.systemDefaultZone()
        );
    }

    ConsultationService(
            ConsultationRepository consultationRepository,
            AppointmentRepository appointmentRepository,
            PatientService patientService,
            CommunicationService communicationService,
            FeedbackService feedbackService,
            Clock clock
    ) {
        this(
                consultationRepository,
                appointmentRepository,
                patientService,
                communicationService,
                feedbackService,
                null,
                clock
        );
    }

    @Autowired
    ConsultationService(
            ConsultationRepository consultationRepository,
            AppointmentRepository appointmentRepository,
            PatientService patientService,
            CommunicationService communicationService,
            FeedbackService feedbackService,
            ClinicalAccessService clinicalAccessService,
            Clock clock
    ) {
        this.consultationRepository = consultationRepository;
        this.appointmentRepository = appointmentRepository;
        this.patientService = patientService;
        this.communicationService = communicationService;
        this.feedbackService = feedbackService;
        this.clinicalAccessService = clinicalAccessService;
        this.clock = clock;
    }

    @Transactional
    public ConsultationResponse startConsultation(UUID appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        validateAppointmentCanStartConsultation(appointment);
        if (consultationRepository.existsByAppointmentId(appointmentId)) {
            throw new DuplicateResourceException("Consultation already exists for appointment");
        }

        try {
            Consultation consultation = new Consultation(appointment, Instant.now(clock));
            return toResponse(consultationRepository.save(consultation));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("Consultation already exists for appointment");
        }
    }

    @Transactional(readOnly = true)
    public ConsultationResponse getConsultation(UUID id) {
        return toResponse(findConsultation(id));
    }

    @Transactional(readOnly = true)
    public ConsultationResponse getAppointmentConsultation(UUID appointmentId) {
        Optional<UUID> scopedDoctorId = scopedDoctorId();
        Optional<Consultation> consultation = scopedDoctorId
                .flatMap(doctorId -> consultationRepository.findByAppointmentIdAndAppointmentDoctorId(appointmentId, doctorId));
        if (scopedDoctorId.isEmpty()) {
            consultation = consultationRepository.findByAppointmentId(appointmentId);
        }
        return consultation
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found"));
    }

    @Transactional(readOnly = true)
    public Page<ConsultationResponse> getPatientConsultations(UUID patientId, Pageable pageable) {
        requirePatientAccess(patientId);
        patientService.findPatientEntity(patientId);
        Optional<UUID> scopedDoctorId = scopedDoctorId();
        if (scopedDoctorId.isPresent()) {
            return consultationRepository.findByAppointmentPatientIdAndAppointmentDoctorId(
                    patientId,
                    scopedDoctorId.get(),
                    pageable
            ).map(this::toResponse);
        }
        return consultationRepository.findByAppointmentPatientId(patientId, pageable).map(this::toResponse);
    }

    @Transactional
    public ConsultationResponse updateConsultation(UUID id, ConsultationUpdateRequest request) {
        Consultation consultation = findConsultation(id);
        validateInProgress(consultation);
        consultation.updateClinicalNotes(
                nullableTrim(request.chiefComplaint()),
                nullableTrim(request.symptoms()),
                nullableTrim(request.examination()),
                nullableTrim(request.assessment()),
                nullableTrim(request.treatment()),
                nullableTrim(request.followUpInstructions())
        );
        return toResponse(consultation);
    }

    @Transactional
    public ConsultationResponse completeConsultation(UUID id) {
        Consultation consultation = findConsultation(id);
        if (consultation.getStatus() == ConsultationStatus.COMPLETED) {
            return toResponse(consultation);
        }
        validateInProgress(consultation);
        validateReadyForCompletion(consultation);
        consultation.complete(Instant.now(clock));
        consultation.getAppointment().setStatus(AppointmentStatus.COMPLETED);
        com.patientmanagement.common.AfterCommitAction.run(
                () -> communicationService.createConsultationCompleted(consultation.getAppointment()));
        com.patientmanagement.common.AfterCommitAction.run(
                () -> feedbackService.createFeedbackRequestForCompletedConsultation(consultation));
        return toResponse(consultation);
    }

    private Appointment findAppointment(UUID appointmentId) {
        if (appointmentId == null) {
            throw new InvalidRequestException("Appointment is required");
        }
        Optional<UUID> scopedDoctorId = scopedDoctorId();
        Optional<Appointment> appointment = scopedDoctorId
                .flatMap(doctorId -> appointmentRepository.findByIdAndDoctorId(appointmentId, doctorId));
        if (scopedDoctorId.isEmpty()) {
            appointment = appointmentRepository.findById(appointmentId);
        }
        return appointment
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private Consultation findConsultation(UUID id) {
        Optional<UUID> scopedDoctorId = scopedDoctorId();
        Optional<Consultation> consultation = scopedDoctorId
                .flatMap(doctorId -> consultationRepository.findByIdAndAppointmentDoctorId(id, doctorId));
        if (scopedDoctorId.isEmpty()) {
            consultation = consultationRepository.findById(id);
        }
        return consultation
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found"));
    }

    private Optional<UUID> scopedDoctorId() {
        return clinicalAccessService == null ? Optional.empty() : clinicalAccessService.scopedDoctorId();
    }

    private void requirePatientAccess(UUID patientId) {
        if (clinicalAccessService != null) {
            clinicalAccessService.requirePatientAccess(patientId);
        }
    }

    private void validateAppointmentCanStartConsultation(Appointment appointment) {
        if (!CONSULTATION_ELIGIBLE_APPOINTMENT_STATUSES.contains(appointment.getStatus())) {
            throw new InvalidRequestException("Consultation can only be started for scheduled or confirmed appointments");
        }
    }

    private void validateInProgress(Consultation consultation) {
        if (consultation.getStatus() != ConsultationStatus.IN_PROGRESS) {
            throw new InvalidRequestException("Only in-progress consultations can be modified");
        }
    }

    private void validateReadyForCompletion(Consultation consultation) {
        if (isBlank(consultation.getChiefComplaint())) {
            throw new InvalidRequestException("Chief complaint is required before completion");
        }
        if (isBlank(consultation.getAssessment())) {
            throw new InvalidRequestException("Assessment is required before completion");
        }
    }

    private ConsultationResponse toResponse(Consultation consultation) {
        Appointment appointment = consultation.getAppointment();
        Patient patient = appointment.getPatient();
        Doctor doctor = appointment.getDoctor();
        return new ConsultationResponse(
                consultation.getId(),
                appointment.getId(),
                patient.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctor.getId(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                appointment.getAppointmentDateTime(),
                appointment.getStatus(),
                consultation.getStatus(),
                consultation.getChiefComplaint(),
                consultation.getSymptoms(),
                consultation.getExamination(),
                consultation.getAssessment(),
                consultation.getTreatment(),
                consultation.getFollowUpInstructions(),
                consultation.getStartedAt(),
                consultation.getCompletedAt(),
                consultation.getCreatedAt(),
                consultation.getUpdatedAt()
        );
    }

    private String nullableTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
