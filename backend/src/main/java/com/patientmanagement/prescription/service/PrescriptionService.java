package com.patientmanagement.prescription.service;

import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.dto.PrescriptionItemRequest;
import com.patientmanagement.prescription.dto.PrescriptionItemResponse;
import com.patientmanagement.prescription.dto.PrescriptionRequest;
import com.patientmanagement.prescription.dto.PrescriptionResponse;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.model.PrescriptionItem;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientService patientService;
    private final DoctorService doctorService;

    public PrescriptionService(
            PrescriptionRepository prescriptionRepository,
            PatientService patientService,
            DoctorService doctorService
    ) {
        this.prescriptionRepository = prescriptionRepository;
        this.patientService = patientService;
        this.doctorService = doctorService;
    }

    @Transactional
    public PrescriptionResponse createPrescription(PrescriptionRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new InvalidRequestException("Prescription must contain at least one item");
        }

        Patient patient = patientService.findPatientEntity(request.patientId());
        Doctor doctor = doctorService.findDoctorEntity(request.doctorId());

        Prescription prescription = new Prescription(patient, doctor, request.prescriptionDate(), request.notes());
        request.items().stream()
                .map(this::toEntity)
                .forEach(prescription::addItem);

        return toResponse(prescriptionRepository.save(prescription));
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse getPrescription(UUID id) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
        return toResponse(prescription);
    }

    @Transactional(readOnly = true)
    public Page<PrescriptionResponse> getPatientPrescriptions(UUID patientId, Pageable pageable) {
        patientService.findPatientEntity(patientId);
        return prescriptionRepository.findByPatientId(patientId, pageable).map(this::toResponse);
    }

    private PrescriptionItem toEntity(PrescriptionItemRequest request) {
        return new PrescriptionItem(
                request.medicineName(),
                request.dosage(),
                request.frequency(),
                request.duration(),
                request.instructions()
        );
    }

    private PrescriptionResponse toResponse(Prescription prescription) {
        Patient patient = prescription.getPatient();
        Doctor doctor = prescription.getDoctor();
        List<PrescriptionItemResponse> items = prescription.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return new PrescriptionResponse(
                prescription.getId(),
                patient.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctor.getId(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                prescription.getPrescriptionDate(),
                prescription.getNotes(),
                items,
                prescription.getCreatedAt(),
                prescription.getUpdatedAt()
        );
    }

    private PrescriptionItemResponse toItemResponse(PrescriptionItem item) {
        return new PrescriptionItemResponse(
                item.getId(),
                item.getMedicineName(),
                item.getDosage(),
                item.getFrequency(),
                item.getDuration(),
                item.getInstructions()
        );
    }
}
