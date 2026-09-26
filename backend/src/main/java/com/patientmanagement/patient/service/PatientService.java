package com.patientmanagement.patient.service;

import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.patient.dto.PatientRequest;
import com.patientmanagement.patient.dto.PatientResponse;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.repository.PatientRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final ClinicalAccessService clinicalAccessService;

    @Autowired
    public PatientService(PatientRepository patientRepository, ClinicalAccessService clinicalAccessService) {
        this.patientRepository = patientRepository;
        this.clinicalAccessService = clinicalAccessService;
    }

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
        this.clinicalAccessService = null;
    }

    @Transactional
    public PatientResponse createPatient(PatientRequest request) {
        Patient patient = new Patient(
                request.firstName().trim(),
                request.lastName().trim(),
                request.dateOfBirth(),
                request.gender(),
                request.bloodGroup(),
                request.phone().trim(),
                trimToNull(request.email()),
                trimToNull(request.address()),
                trimToNull(request.emergencyContactName()),
                trimToNull(request.emergencyContactPhone())
        );
        return toResponse(patientRepository.save(patient));
    }

    @Transactional(readOnly = true)
    public Page<PatientResponse> getPatients(Pageable pageable) {
        if (clinicalAccessService != null && clinicalAccessService.scopedDoctorId().isPresent()) {
            return patientRepository.findPatientsForDoctor(clinicalAccessService.currentDoctorId(), pageable)
                    .map(this::toResponse);
        }
        return patientRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatient(UUID id) {
        if (clinicalAccessService != null && clinicalAccessService.scopedDoctorId().isPresent()) {
            return patientRepository.findByIdForDoctor(id, clinicalAccessService.currentDoctorId())
                    .map(this::toResponse)
                    .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        }
        return toResponse(findPatientEntity(id));
    }

    @Transactional
    public PatientResponse updatePatient(UUID id, PatientRequest request) {
        Patient patient = findPatientEntity(id);
        patient.setFirstName(request.firstName().trim());
        patient.setLastName(request.lastName().trim());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender());
        patient.setBloodGroup(request.bloodGroup());
        patient.setPhone(request.phone().trim());
        patient.setEmail(trimToNull(request.email()));
        patient.setAddress(trimToNull(request.address()));
        patient.setEmergencyContactName(trimToNull(request.emergencyContactName()));
        patient.setEmergencyContactPhone(trimToNull(request.emergencyContactPhone()));
        return toResponse(patient);
    }

    @Transactional
    public void deletePatient(UUID id) {
        Patient patient = findPatientEntity(id);
        patientRepository.delete(patient);
    }

    @Transactional(readOnly = true)
    public Patient findPatientEntity(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }

    private PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getDateOfBirth(),
                patient.getGender(),
                patient.getBloodGroup(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getAddress(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactPhone(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
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
