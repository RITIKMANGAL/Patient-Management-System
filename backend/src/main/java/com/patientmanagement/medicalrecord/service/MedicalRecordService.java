package com.patientmanagement.medicalrecord.service;

import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.medicalrecord.dto.MedicalRecordRequest;
import com.patientmanagement.medicalrecord.dto.MedicalRecordResponse;
import com.patientmanagement.medicalrecord.model.MedicalRecord;
import com.patientmanagement.medicalrecord.repository.MedicalRecordRepository;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.service.PatientService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientService patientService;
    private final DoctorService doctorService;

    public MedicalRecordService(
            MedicalRecordRepository medicalRecordRepository,
            PatientService patientService,
            DoctorService doctorService
    ) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.patientService = patientService;
        this.doctorService = doctorService;
    }

    @Transactional
    public MedicalRecordResponse createMedicalRecord(MedicalRecordRequest request) {
        Patient patient = patientService.findPatientEntity(request.patientId());
        Doctor doctor = doctorService.findDoctorEntity(request.doctorId());

        MedicalRecord medicalRecord = new MedicalRecord(
                patient,
                doctor,
                request.diagnosis(),
                request.symptoms(),
                request.notes(),
                request.recordDate()
        );

        return toResponse(medicalRecordRepository.save(medicalRecord));
    }

    @Transactional(readOnly = true)
    public MedicalRecordResponse getMedicalRecord(UUID id) {
        MedicalRecord medicalRecord = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medical record not found"));
        return toResponse(medicalRecord);
    }

    @Transactional(readOnly = true)
    public Page<MedicalRecordResponse> getPatientMedicalRecords(UUID patientId, Pageable pageable) {
        patientService.findPatientEntity(patientId);
        return medicalRecordRepository.findByPatientId(patientId, pageable).map(this::toResponse);
    }

    private MedicalRecordResponse toResponse(MedicalRecord medicalRecord) {
        Patient patient = medicalRecord.getPatient();
        Doctor doctor = medicalRecord.getDoctor();

        return new MedicalRecordResponse(
                medicalRecord.getId(),
                patient.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctor.getId(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                medicalRecord.getDiagnosis(),
                medicalRecord.getSymptoms(),
                medicalRecord.getNotes(),
                medicalRecord.getRecordDate(),
                medicalRecord.getCreatedAt(),
                medicalRecord.getUpdatedAt()
        );
    }
}
