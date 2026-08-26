package com.patientmanagement.patient.service;

import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.patient.dto.PatientRequest;
import com.patientmanagement.patient.dto.PatientResponse;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.repository.PatientRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional
    public PatientResponse createPatient(PatientRequest request) {
        Patient patient = new Patient(
                request.firstName(),
                request.lastName(),
                request.dateOfBirth(),
                request.gender(),
                request.bloodGroup(),
                request.phone(),
                request.email(),
                request.address(),
                request.emergencyContactName(),
                request.emergencyContactPhone()
        );
        return toResponse(patientRepository.save(patient));
    }

    @Transactional(readOnly = true)
    public Page<PatientResponse> getPatients(Pageable pageable) {
        return patientRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatient(UUID id) {
        return toResponse(findPatientEntity(id));
    }

    @Transactional
    public PatientResponse updatePatient(UUID id, PatientRequest request) {
        Patient patient = findPatientEntity(id);
        patient.setFirstName(request.firstName());
        patient.setLastName(request.lastName());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender());
        patient.setBloodGroup(request.bloodGroup());
        patient.setPhone(request.phone());
        patient.setEmail(request.email());
        patient.setAddress(request.address());
        patient.setEmergencyContactName(request.emergencyContactName());
        patient.setEmergencyContactPhone(request.emergencyContactPhone());
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
}
