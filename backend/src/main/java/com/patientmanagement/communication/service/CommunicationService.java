package com.patientmanagement.communication.service;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.communication.config.SmsProperties;
import com.patientmanagement.communication.dto.CommunicationCreateRequest;
import com.patientmanagement.communication.dto.CommunicationResponse;
import com.patientmanagement.communication.model.Communication;
import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationStatus;
import com.patientmanagement.communication.model.CommunicationType;
import com.patientmanagement.communication.provider.CommunicationDispatchRequest;
import com.patientmanagement.communication.provider.CommunicationProvider;
import com.patientmanagement.communication.provider.CommunicationProviderResult;
import com.patientmanagement.communication.repository.CommunicationRepository;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.model.Prescription;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationService {

    private static final Set<CommunicationStatus> NON_FAILED_STATUSES =
            EnumSet.of(CommunicationStatus.PENDING, CommunicationStatus.SENT, CommunicationStatus.DELIVERED,
                    CommunicationStatus.SIMULATED, CommunicationStatus.DISABLED);
    private static final Set<AppointmentStatus> REMINDER_ELIGIBLE_STATUSES =
            EnumSet.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);

    private final CommunicationRepository communicationRepository;
    private final PatientService patientService;
    private final AppointmentRepository appointmentRepository;
    private final Map<CommunicationChannel, CommunicationProvider> providers;
    private final SmsProperties smsProperties;
    private final Clock clock;

    public CommunicationService(
            CommunicationRepository communicationRepository,
            PatientService patientService,
            AppointmentRepository appointmentRepository,
            List<CommunicationProvider> providers,
            SmsProperties smsProperties
    ) {
        this(communicationRepository, patientService, appointmentRepository, providers, smsProperties, Clock.systemDefaultZone());
    }

    @Autowired
    CommunicationService(
            CommunicationRepository communicationRepository,
            PatientService patientService,
            AppointmentRepository appointmentRepository,
            List<CommunicationProvider> providers,
            SmsProperties smsProperties,
            Clock clock
    ) {
        this.communicationRepository = communicationRepository;
        this.patientService = patientService;
        this.appointmentRepository = appointmentRepository;
        this.providers = providerMap(providers);
        this.smsProperties = smsProperties;
        this.clock = clock;
    }

    @Transactional
    public CommunicationResponse createCommunication(CommunicationCreateRequest request) {
        validateRequest(request);

        Patient patient = patientService.findPatientEntity(request.patientId());
        Appointment appointment = findAppointment(request.appointmentId());
        validateAppointmentBelongsToPatient(patient, appointment);
        validateNoActiveDuplicate(request, appointment);

        Communication communication = new Communication(
                patient,
                appointment,
                request.type(),
                request.channel(),
                request.recipient().trim()
        );
        communicationRepository.save(communication);
        dispatchIfProviderExists(communication);
        return toResponse(communication);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public CommunicationResponse createAppointmentConfirmation(Appointment appointment) {
        return createAppointmentCommunication(appointment, CommunicationType.APPOINTMENT_CONFIRMATION);
    }

    @Transactional
    public CommunicationResponse createAppointmentReminder(Appointment appointment) {
        return createAppointmentCommunication(appointment, CommunicationType.APPOINTMENT_REMINDER);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void createConsultationCompleted(Appointment appointment) {
        validateAppointmentForPatientCommunication(appointment);
        Patient patient = appointment.getPatient();
        if (hasActiveDuplicate(
                patient.getId(),
                appointment.getId(),
                CommunicationType.CONSULTATION_COMPLETED,
                CommunicationChannel.SMS
        )) {
            return;
        }
        String recipient = patient.getPhone() == null ? "" : patient.getPhone().trim();
        if (recipient.isBlank()) {
            Communication communication = new Communication(
                    patient,
                    appointment,
                    CommunicationType.CONSULTATION_COMPLETED,
                    CommunicationChannel.SMS,
                    recipient
            );
            communicationRepository.save(communication);
            communication.markFailed("Communication recipient is required");
            return;
        }
        persistAppointmentCommunication(appointment, CommunicationType.CONSULTATION_COMPLETED);
    }

    @Transactional
    public void createFeedbackRequest(Appointment appointment, String feedbackUrl) {
        validateAppointmentForPatientCommunication(appointment);
        if (feedbackUrl == null || feedbackUrl.isBlank()) {
            throw new InvalidRequestException("Feedback URL is required");
        }
        Patient patient = appointment.getPatient();
        if (hasActiveDuplicate(
                patient.getId(),
                appointment.getId(),
                CommunicationType.FEEDBACK_REQUEST,
                CommunicationChannel.SMS
        )) {
            return;
        }
        String recipient = patient.getPhone() == null ? "" : patient.getPhone().trim();
        Communication communication = new Communication(
                patient,
                appointment,
                CommunicationType.FEEDBACK_REQUEST,
                CommunicationChannel.SMS,
                recipient
        );
        communicationRepository.save(communication);
        if (recipient.isBlank()) {
            communication.markFailed("Communication recipient is required");
            return;
        }
        dispatchIfProviderExists(
                communication,
                "Please share feedback about your recent clinic visit: " + feedbackUrl
        );
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public CommunicationResponse createPrescriptionAvailable(Prescription prescription) {
        validatePrescriptionCommunication(prescription);
        Patient patient = prescription.getPatient();
        String recipient = patient.getPhone() == null ? "" : patient.getPhone().trim();
        if (recipient.isBlank()) {
            Communication communication = new Communication(
                    patient,
                    null,
                    CommunicationType.PRESCRIPTION_AVAILABLE,
                    CommunicationChannel.SMS,
                    recipient
            );
            communicationRepository.save(communication);
            communication.markFailed("Communication recipient is required");
            return toResponse(communication);
        }
        return persistPatientCommunication(patient, CommunicationType.PRESCRIPTION_AVAILABLE, recipient);
    }

    @Transactional
    public List<CommunicationResponse> createDueAppointmentReminders(LocalDateTime from, LocalDateTime to) {
        validateReminderWindow(from, to);

        List<CommunicationResponse> reminders = new ArrayList<>();
        List<Appointment> appointments = appointmentRepository
                .findByAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusIn(
                        from,
                        to,
                        REMINDER_ELIGIBLE_STATUSES
                );
        for (Appointment appointment : appointments) {
            Patient patient = appointment.getPatient();
            if (hasActiveDuplicate(
                    patient.getId(),
                    appointment.getId(),
                    CommunicationType.APPOINTMENT_REMINDER,
                    CommunicationChannel.SMS
            )) {
                continue;
            }
            reminders.add(persistAppointmentCommunication(appointment, CommunicationType.APPOINTMENT_REMINDER));
        }
        return reminders;
    }

    @Transactional(readOnly = true)
    public Page<CommunicationResponse> getCommunications(Pageable pageable) {
        return communicationRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CommunicationResponse getCommunication(UUID id) {
        Communication communication = communicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Communication not found"));
        return toResponse(communication);
    }

    private void validateRequest(CommunicationCreateRequest request) {
        if (request.patientId() == null) {
            throw new InvalidRequestException("Patient is required");
        }
        if (request.type() == null) {
            throw new InvalidRequestException("Communication type is required");
        }
        if (request.channel() == null) {
            throw new InvalidRequestException("Communication channel is required");
        }
        if (request.recipient() == null || request.recipient().isBlank()) {
            throw new InvalidRequestException("Communication recipient is required");
        }
    }

    private void validateAppointmentCommunication(Appointment appointment) {
        validateAppointmentForPatientCommunication(appointment);
    }

    private void validateAppointmentForPatientCommunication(Appointment appointment) {
        if (appointment == null || appointment.getId() == null) {
            throw new InvalidRequestException("Appointment is required");
        }
        Patient patient = appointment.getPatient();
        if (patient == null || patient.getId() == null) {
            throw new InvalidRequestException("Patient is required");
        }
    }

    private void validatePrescriptionCommunication(Prescription prescription) {
        if (prescription == null) {
            throw new InvalidRequestException("Prescription is required");
        }
        Patient patient = prescription.getPatient();
        if (patient == null || patient.getId() == null) {
            throw new InvalidRequestException("Patient is required");
        }
    }

    private void validateReminderWindow(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new InvalidRequestException("Reminder date range is required");
        }
        if (!from.isBefore(to)) {
            throw new InvalidRequestException("Reminder date range is invalid");
        }
    }

    private Appointment findAppointment(UUID appointmentId) {
        if (appointmentId == null) {
            return null;
        }
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private void validateAppointmentBelongsToPatient(Patient patient, Appointment appointment) {
        if (appointment != null && !appointment.getPatient().getId().equals(patient.getId())) {
            throw new InvalidRequestException("Appointment does not belong to patient");
        }
    }

    private void validateNoActiveDuplicate(CommunicationCreateRequest request, Appointment appointment) {
        if (appointment == null) {
            return;
        }
        validateNoActiveDuplicate(
                request.patientId(),
                request.appointmentId(),
                request.type(),
                request.channel()
        );
    }

    private void validateNoActiveDuplicate(
            UUID patientId,
            UUID appointmentId,
            CommunicationType type,
            CommunicationChannel channel
    ) {
        if (hasActiveDuplicate(
                patientId,
                appointmentId,
                type,
                channel
        )) {
            throw new DuplicateResourceException("Communication already exists for this patient and appointment");
        }
    }

    private boolean hasActiveDuplicate(
            UUID patientId,
            UUID appointmentId,
            CommunicationType type,
            CommunicationChannel channel
    ) {
        return communicationRepository.existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                patientId,
                appointmentId,
                type,
                channel,
                NON_FAILED_STATUSES
        );
    }

    private CommunicationResponse createAppointmentCommunication(Appointment appointment, CommunicationType type) {
        validateAppointmentCommunication(appointment);
        Patient patient = appointment.getPatient();
        validateNoActiveDuplicate(
                patient.getId(),
                appointment.getId(),
                type,
                CommunicationChannel.SMS
        );
        String recipient = patient.getPhone() == null ? "" : patient.getPhone().trim();
        if (recipient.isBlank()) {
            Communication communication = new Communication(
                    patient,
                    appointment,
                    type,
                    CommunicationChannel.SMS,
                    recipient
            );
            communicationRepository.save(communication);
            communication.markFailed("Communication recipient is required");
            return toResponse(communication);
        }
        return persistAppointmentCommunication(appointment, type);
    }

    private CommunicationResponse persistAppointmentCommunication(Appointment appointment, CommunicationType type) {
        Patient patient = appointment.getPatient();
        Communication communication = new Communication(
                patient,
                appointment,
                type,
                CommunicationChannel.SMS,
                patient.getPhone().trim()
        );
        communicationRepository.save(communication);
        dispatchIfProviderExists(communication);
        return toResponse(communication);
    }

    private CommunicationResponse persistPatientCommunication(
            Patient patient,
            CommunicationType type,
            String recipient
    ) {
        Communication communication = new Communication(
                patient,
                null,
                type,
                CommunicationChannel.SMS,
                recipient
        );
        communicationRepository.save(communication);
        dispatchIfProviderExists(communication);
        return toResponse(communication);
    }

    private void dispatchIfProviderExists(Communication communication) {
        dispatchIfProviderExists(communication, messageFor(communication.getType()));
    }

    private void dispatchIfProviderExists(Communication communication, String message) {
        if (communication.getChannel() == CommunicationChannel.SMS && !smsProperties.enabled()) {
            communication.markDisabled();
            return;
        }
        CommunicationProvider provider = providers.get(communication.getChannel());
        if (provider == null) {
            communication.markFailed("Communication provider is not configured");
            return;
        }

        try {
            CommunicationProviderResult result = provider.send(new CommunicationDispatchRequest(
                    communication.getId(),
                    communication.getType(),
                    communication.getChannel(),
                    communication.getRecipient(),
                    message,
                    smsProperties.sender(),
                    smsProperties.templateIdFor(communication.getType())
            ));
            if (result.simulated()) {
                communication.markSimulated();
            } else if (result.successful()) {
                communication.markSent(result.providerMessageId(), Instant.now(clock));
            } else {
                communication.markFailed(result.failureReason());
            }
        } catch (RuntimeException exception) {
            communication.markFailed("Communication provider failed");
        }
    }

    private CommunicationResponse toResponse(Communication communication) {
        Patient patient = communication.getPatient();
        Appointment appointment = communication.getAppointment();
        return new CommunicationResponse(
                communication.getId(),
                patient.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                appointment == null ? null : appointment.getId(),
                communication.getType(),
                communication.getChannel(),
                communication.getRecipient(),
                communication.getStatus(),
                communication.getProviderMessageId(),
                communication.getFailureReason(),
                communication.getAttemptCount(),
                communication.getSentAt(),
                communication.getDeliveredAt(),
                communication.getCreatedAt(),
                communication.getUpdatedAt()
        );
    }

    private static Map<CommunicationChannel, CommunicationProvider> providerMap(List<CommunicationProvider> providers) {
        Map<CommunicationChannel, CommunicationProvider> mappedProviders = new EnumMap<>(CommunicationChannel.class);
        for (CommunicationProvider provider : providers) {
            mappedProviders.put(provider.channel(), provider);
        }
        return mappedProviders;
    }

    private String messageFor(CommunicationType type) {
        return switch (type) {
            case APPOINTMENT_CONFIRMATION ->
                    "Your appointment has been scheduled. Please contact the clinic if you have any questions.";
            case APPOINTMENT_REMINDER ->
                    "Reminder: you have an upcoming appointment. Please contact the clinic if you have any questions.";
            case CONSULTATION_COMPLETED ->
                    "Your consultation has been completed. Please contact the clinic if you have any questions.";
            case PRESCRIPTION_AVAILABLE ->
                    "Your prescription is available. Please contact the clinic if you have any questions.";
            case FEEDBACK_REQUEST ->
                    "Please contact the clinic if you have feedback about your recent visit.";
        };
    }
}
