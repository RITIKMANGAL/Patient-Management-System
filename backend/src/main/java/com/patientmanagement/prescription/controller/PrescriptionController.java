package com.patientmanagement.prescription.controller;

import com.patientmanagement.prescription.dto.PrescriptionRequest;
import com.patientmanagement.prescription.dto.PrescriptionResponse;
import com.patientmanagement.prescription.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Prescriptions", description = "Core prescription APIs")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @PostMapping("/api/v1/prescriptions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Create a prescription")
    public ResponseEntity<PrescriptionResponse> createPrescription(
            @Valid @RequestBody PrescriptionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prescriptionService.createPrescription(request));
    }

    @GetMapping("/api/v1/prescriptions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Get a prescription by ID")
    public PrescriptionResponse getPrescription(@PathVariable UUID id) {
        return prescriptionService.getPrescription(id);
    }

    @GetMapping("/api/v1/patients/{patientId}/prescriptions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "List prescriptions for a patient")
    public Page<PrescriptionResponse> getPatientPrescriptions(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20, sort = "prescriptionDate") Pageable pageable
    ) {
        return prescriptionService.getPatientPrescriptions(patientId, pageable);
    }
}
