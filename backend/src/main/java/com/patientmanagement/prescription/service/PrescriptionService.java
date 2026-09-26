package com.patientmanagement.prescription.service;

import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.communication.service.CommunicationService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientService patientService;
    private final DoctorService doctorService;
    private final CommunicationService communicationService;
    private final ClinicalAccessService clinicalAccessService;

    @Autowired
    public PrescriptionService(
            PrescriptionRepository prescriptionRepository,
            PatientService patientService,
            DoctorService doctorService,
            CommunicationService communicationService,
            ClinicalAccessService clinicalAccessService
    ) {
        this.prescriptionRepository = prescriptionRepository;
        this.patientService = patientService;
        this.doctorService = doctorService;
        this.communicationService = communicationService;
        this.clinicalAccessService = clinicalAccessService;
    }

    public PrescriptionService(
            PrescriptionRepository prescriptionRepository,
            PatientService patientService,
            DoctorService doctorService,
            CommunicationService communicationService
    ) {
        this(prescriptionRepository, patientService, doctorService, communicationService, null);
    }

    @Transactional
    public PrescriptionResponse createPrescription(PrescriptionRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new InvalidRequestException("Prescription must contain at least one item");
        }

        requireDoctorMatches(request.doctorId());
        requirePatientAccess(request.patientId());
        Patient patient = patientService.findPatientEntity(request.patientId());
        Doctor doctor = doctorService.findDoctorEntity(request.doctorId());

        Prescription prescription = new Prescription(
                patient,
                doctor,
                request.prescriptionDate(),
                trimToNull(request.notes())
        );
        request.items().stream()
                .map(this::toEntity)
                .forEach(prescription::addItem);

        Prescription savedPrescription = prescriptionRepository.save(prescription);
        com.patientmanagement.common.AfterCommitAction.run(
                () -> communicationService.createPrescriptionAvailable(savedPrescription));
        return toResponse(savedPrescription);
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse getPrescription(UUID id) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
        requirePrescriptionAccess(prescription);
        return toResponse(prescription);
    }

    @Transactional(readOnly = true)
    public Page<PrescriptionResponse> getPatientPrescriptions(UUID patientId, Pageable pageable) {
        requirePatientAccess(patientId);
        patientService.findPatientEntity(patientId);
        return prescriptionRepository.findByPatientId(patientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Prescription findPrescriptionEntity(UUID id) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
        requirePrescriptionAccess(prescription);
        return prescription;
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

    private void requirePrescriptionAccess(Prescription prescription) {
        if (clinicalAccessService != null) {
            clinicalAccessService.requirePrescriptionAccess(prescription);
        }
    }

    private PrescriptionItem toEntity(PrescriptionItemRequest request) {
        return new PrescriptionItem(
                request.medicineName().trim(),
                request.dosage().trim(),
                request.frequency().trim(),
                request.duration().trim(),
                trimToNull(request.instructions())
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
