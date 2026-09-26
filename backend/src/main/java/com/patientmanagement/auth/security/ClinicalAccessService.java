package com.patientmanagement.auth.security;

import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.doctor.repository.DoctorRepository;
import com.patientmanagement.medicalrecord.model.MedicalRecord;
import com.patientmanagement.prescription.model.Prescription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ClinicalAccessService {

    private final CurrentUserService currentUserService;
    private final Optional<DoctorRepository> doctorRepository;
    private final Optional<AppointmentRepository> appointmentRepository;

    @Autowired
    public ClinicalAccessService(
            CurrentUserService currentUserService,
            Optional<DoctorRepository> doctorRepository,
            Optional<AppointmentRepository> appointmentRepository
    ) {
        this.currentUserService = currentUserService;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public ClinicalAccessService(
            CurrentUserService currentUserService,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository
    ) {
        this.currentUserService = currentUserService;
        this.doctorRepository = Optional.of(doctorRepository);
        this.appointmentRepository = Optional.of(appointmentRepository);
    }

    public boolean doctorScopeRequired() {
        return currentUserService.hasRole(RoleName.DOCTOR)
                && !currentUserService.hasRole(RoleName.ADMIN)
                && !currentUserService.hasRole(RoleName.RECEPTIONIST);
    }

    public Optional<UUID> scopedDoctorId() {
        if (!doctorScopeRequired()) {
            return Optional.empty();
        }
        return Optional.of(currentDoctorId());
    }

    public UUID currentDoctorId() {
        UUID userId = currentUserService.currentUserId()
                .orElseThrow(() -> new AccessDeniedException("Doctor user is not linked"));
        return doctorRepository
                .orElseThrow(() -> new AccessDeniedException("Doctor user is not linked"))
                .findByUserId(userId)
                .map(doctor -> doctor.getId())
                .orElseThrow(() -> new AccessDeniedException("Doctor user is not linked"));
    }

    public void requireDoctorMatches(UUID doctorId) {
        scopedDoctorId().ifPresent(scopedDoctorId -> {
            if (!scopedDoctorId.equals(doctorId)) {
                throw new ResourceNotFoundException("Doctor not found");
            }
        });
    }

    public void requirePatientAccess(UUID patientId) {
        scopedDoctorId().ifPresent(scopedDoctorId -> {
            AppointmentRepository repository = appointmentRepository
                    .orElseThrow(() -> new AccessDeniedException("Doctor user is not linked"));
            if (!repository.existsByPatientIdAndDoctorId(patientId, scopedDoctorId)) {
                throw new ResourceNotFoundException("Patient not found");
            }
        });
    }

    public void requireConsultationAccess(Consultation consultation) {
        scopedDoctorId().ifPresent(scopedDoctorId -> {
            if (!scopedDoctorId.equals(consultation.getAppointment().getDoctor().getId())) {
                throw new ResourceNotFoundException("Consultation not found");
            }
        });
    }

    public void requireMedicalRecordAccess(MedicalRecord medicalRecord) {
        scopedDoctorId().ifPresent(scopedDoctorId -> {
            UUID patientId = medicalRecord.getPatient().getId();
            AppointmentRepository repository = appointmentRepository
                    .orElseThrow(() -> new AccessDeniedException("Doctor user is not linked"));
            if (!repository.existsByPatientIdAndDoctorId(patientId, scopedDoctorId)) {
                throw new ResourceNotFoundException("Medical record not found");
            }
        });
    }

    public void requirePrescriptionAccess(Prescription prescription) {
        scopedDoctorId().ifPresent(scopedDoctorId -> {
            UUID patientId = prescription.getPatient().getId();
            AppointmentRepository repository = appointmentRepository
                    .orElseThrow(() -> new AccessDeniedException("Doctor user is not linked"));
            if (!repository.existsByPatientIdAndDoctorId(patientId, scopedDoctorId)) {
                throw new ResourceNotFoundException("Prescription not found");
            }
        });
    }
}
