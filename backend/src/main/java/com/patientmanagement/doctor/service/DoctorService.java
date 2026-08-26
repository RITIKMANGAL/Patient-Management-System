package com.patientmanagement.doctor.service;

import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.dto.DoctorRequest;
import com.patientmanagement.doctor.dto.DoctorResponse;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.repository.DoctorRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @Transactional
    public DoctorResponse createDoctor(DoctorRequest request) {
        if (doctorRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new DuplicateResourceException("Doctor license number already exists");
        }

        Doctor doctor = new Doctor(
                request.firstName(),
                request.lastName(),
                request.specialization(),
                request.licenseNumber(),
                request.phone(),
                request.email(),
                request.department()
        );
        return toResponse(doctorRepository.save(doctor));
    }

    @Transactional(readOnly = true)
    public Page<DoctorResponse> getDoctors(Pageable pageable) {
        return doctorRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DoctorResponse getDoctor(UUID id) {
        return toResponse(findDoctorEntity(id));
    }

    @Transactional
    public DoctorResponse updateDoctor(UUID id, DoctorRequest request) {
        if (doctorRepository.existsByLicenseNumberAndIdNot(request.licenseNumber(), id)) {
            throw new DuplicateResourceException("Doctor license number already exists");
        }

        Doctor doctor = findDoctorEntity(id);
        doctor.setFirstName(request.firstName());
        doctor.setLastName(request.lastName());
        doctor.setSpecialization(request.specialization());
        doctor.setLicenseNumber(request.licenseNumber());
        doctor.setPhone(request.phone());
        doctor.setEmail(request.email());
        doctor.setDepartment(request.department());
        return toResponse(doctor);
    }

    @Transactional
    public void deleteDoctor(UUID id) {
        Doctor doctor = findDoctorEntity(id);
        doctorRepository.delete(doctor);
    }

    @Transactional(readOnly = true)
    public Doctor findDoctorEntity(UUID id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
    }

    private DoctorResponse toResponse(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getFirstName(),
                doctor.getLastName(),
                doctor.getSpecialization(),
                doctor.getLicenseNumber(),
                doctor.getPhone(),
                doctor.getEmail(),
                doctor.getDepartment(),
                doctor.getCreatedAt(),
                doctor.getUpdatedAt()
        );
    }
}
