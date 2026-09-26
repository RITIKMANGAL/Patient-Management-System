package com.patientmanagement.appointment.service;

import com.patientmanagement.appointment.dto.AppointmentRequest;
import com.patientmanagement.appointment.dto.AppointmentResponse;
import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.service.PatientService;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

    private static final Duration APPOINTMENT_DURATION = Duration.ofMinutes(30);
    private static final Set<AppointmentStatus> ACTIVE_STATUSES =
            EnumSet.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);

    private final AppointmentRepository appointmentRepository;
    private final PatientService patientService;
    private final DoctorService doctorService;
    private final CommunicationService communicationService;
    private final ClinicalAccessService clinicalAccessService;
    private final Clock clock;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            PatientService patientService,
            DoctorService doctorService,
            CommunicationService communicationService,
            ClinicalAccessService clinicalAccessService
    ) {
        this(
                appointmentRepository,
                patientService,
                doctorService,
                communicationService,
                clinicalAccessService,
                Clock.systemDefaultZone()
        );
    }

    AppointmentService(
            AppointmentRepository appointmentRepository,
            PatientService patientService,
            DoctorService doctorService,
            CommunicationService communicationService,
            Clock clock
    ) {
        this(appointmentRepository, patientService, doctorService, communicationService, null, clock);
    }

    @Autowired
    AppointmentService(
            AppointmentRepository appointmentRepository,
            PatientService patientService,
            DoctorService doctorService,
            CommunicationService communicationService,
            ClinicalAccessService clinicalAccessService,
            Clock clock
    ) {
        this.appointmentRepository = appointmentRepository;
        this.patientService = patientService;
        this.doctorService = doctorService;
        this.communicationService = communicationService;
        this.clinicalAccessService = clinicalAccessService;
        this.clock = clock;
    }

    @Transactional
    public AppointmentResponse createAppointment(AppointmentRequest request) {
        AppointmentStatus status = request.status() == null ? AppointmentStatus.SCHEDULED : request.status();
        validateCreatableStatus(status);
        validateAppointmentDateTime(request.appointmentDateTime());

        Patient patient = patientService.findPatientEntity(request.patientId());
        Doctor doctor = doctorService.findDoctorEntity(request.doctorId());
        validateAvailability(patient.getId(), doctor.getId(), request.appointmentDateTime(), null, status);

        Appointment appointment = new Appointment(
                patient,
                doctor,
                request.appointmentDateTime(),
                request.reason().trim(),
                status,
                trimToNull(request.notes())
        );
        Appointment savedAppointment = appointmentRepository.save(appointment);
        com.patientmanagement.common.AfterCommitAction.run(
                () -> communicationService.createAppointmentConfirmation(savedAppointment));
        return toResponse(savedAppointment);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(UUID id) {
        return toResponse(findAppointmentEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAppointments(
            UUID patientId,
            UUID doctorId,
            AppointmentStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("Appointment date range is invalid");
        }
        return appointmentRepository.findAll(matchesFilters(patientId, doctorId, status, from, to), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public AppointmentResponse updateAppointment(UUID id, AppointmentRequest request) {
        Appointment appointment = findAppointmentEntity(id);
        AppointmentStatus requestedStatus = request.status() == null ? appointment.getStatus() : request.status();

        validateStatusTransition(appointment.getStatus(), requestedStatus);
        validateAppointmentDateTime(request.appointmentDateTime());

        Patient patient = patientService.findPatientEntity(request.patientId());
        Doctor doctor = doctorService.findDoctorEntity(request.doctorId());
        requireDoctorMatches(doctor.getId());
        requirePatientAccess(patient.getId());
        validateAvailability(patient.getId(), doctor.getId(), request.appointmentDateTime(), id, requestedStatus);

        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDateTime(request.appointmentDateTime());
        appointment.setReason(request.reason().trim());
        appointment.setStatus(requestedStatus);
        appointment.setNotes(trimToNull(request.notes()));
        return toResponse(appointment);
    }

    @Transactional
    public void cancelAppointment(UUID id) {
        Appointment appointment = findAppointmentEntity(id);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return;
        }
        validateStatusTransition(appointment.getStatus(), AppointmentStatus.CANCELLED);
        appointment.setStatus(AppointmentStatus.CANCELLED);
    }

    private Appointment findAppointmentEntity(UUID id) {
        Optional<UUID> scopedDoctorId = scopedDoctorId();
        Optional<Appointment> appointment = scopedDoctorId
                .flatMap(doctorId -> appointmentRepository.findByIdAndDoctorId(id, doctorId));
        if (scopedDoctorId.isEmpty()) {
            appointment = appointmentRepository.findById(id);
        }
        return appointment
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private void validateAppointmentDateTime(LocalDateTime appointmentDateTime) {
        if (appointmentDateTime == null || clock.getZone().getRules().getValidOffsets(appointmentDateTime).size() != 1) {
            throw new InvalidRequestException("Appointment time must be unambiguous and valid in the clinic timezone");
        }
        if (appointmentDateTime.isBefore(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("Appointment date and time must not be in the past");
        }
    }

    private void validateCreatableStatus(AppointmentStatus status) {
        if (status != AppointmentStatus.SCHEDULED && status != AppointmentStatus.CONFIRMED) {
            throw new InvalidRequestException("Appointment can only be created as scheduled or confirmed");
        }
    }

    private void validateAvailability(
            UUID patientId,
            UUID doctorId,
            LocalDateTime appointmentDateTime,
            UUID excludedAppointmentId,
            AppointmentStatus status
    ) {
        if (!ACTIVE_STATUSES.contains(status)) {
            return;
        }

        LocalDateTime windowStart = appointmentDateTime.minus(APPOINTMENT_DURATION);
        LocalDateTime windowEnd = appointmentDateTime.plus(APPOINTMENT_DURATION);
        if (appointmentRepository.existsOverlappingDoctorAppointment(
                doctorId,
                windowStart,
                windowEnd,
                ACTIVE_STATUSES,
                excludedAppointmentId
        )) {
            throw new InvalidRequestException("Doctor already has an appointment during this time slot");
        }
        if (appointmentRepository.existsOverlappingPatientAppointment(
                patientId,
                windowStart,
                windowEnd,
                ACTIVE_STATUSES,
                excludedAppointmentId
        )) {
            throw new InvalidRequestException("Patient already has an appointment during this time slot");
        }
    }

    private void validateStatusTransition(AppointmentStatus currentStatus, AppointmentStatus requestedStatus) {
        if (currentStatus == requestedStatus) {
            return;
        }

        boolean valid = switch (currentStatus) {
            case SCHEDULED -> requestedStatus == AppointmentStatus.CONFIRMED
                    || requestedStatus == AppointmentStatus.CANCELLED
                    || requestedStatus == AppointmentStatus.NO_SHOW;
            case CONFIRMED -> requestedStatus == AppointmentStatus.COMPLETED
                    || requestedStatus == AppointmentStatus.CANCELLED
                    || requestedStatus == AppointmentStatus.NO_SHOW;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };

        if (!valid) {
            throw new InvalidRequestException("Appointment status transition is invalid");
        }
    }

    private Specification<Appointment> matchesFilters(
            UUID patientId,
            UUID doctorId,
            AppointmentStatus status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        Optional<UUID> scopedDoctorId = scopedDoctorId();
        UUID effectiveDoctorId = scopedDoctorId.orElse(doctorId);
        boolean impossibleDoctorFilter = scopedDoctorId.isPresent() && doctorId != null && !doctorId.equals(scopedDoctorId.get());
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (impossibleDoctorFilter) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (patientId != null) {
                predicates.add(criteriaBuilder.equal(root.get("patient").get("id"), patientId));
            }
            if (effectiveDoctorId != null) {
                predicates.add(criteriaBuilder.equal(root.get("doctor").get("id"), effectiveDoctorId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("appointmentDateTime"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("appointmentDateTime"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Optional<UUID> scopedDoctorId() {
        return clinicalAccessService == null ? Optional.empty() : clinicalAccessService.scopedDoctorId();
    }

    private void requireDoctorMatches(UUID doctorId) {
        if (clinicalAccessService != null) {
            clinicalAccessService.requireDoctorMatches(doctorId);
        }
    }

    private void requirePatientAccess(UUID patientId) {
        if (clinicalAccessService != null) {
            clinicalAccessService.requirePatientAccess(patientId);
        }
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        Patient patient = appointment.getPatient();
        Doctor doctor = appointment.getDoctor();
        return new AppointmentResponse(
                appointment.getId(),
                patient.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctor.getId(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                appointment.getAppointmentDateTime(),
                appointment.getReason(),
                appointment.getStatus(),
                appointment.getNotes(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
