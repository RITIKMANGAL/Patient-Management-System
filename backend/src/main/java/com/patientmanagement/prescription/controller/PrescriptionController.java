package com.patientmanagement.prescription.controller;

import com.patientmanagement.common.web.PageableSortValidator;
import com.patientmanagement.prescription.dto.PrescriptionRequest;
import com.patientmanagement.prescription.dto.PrescriptionResponse;
import com.patientmanagement.prescription.service.PrescriptionPdfService;
import com.patientmanagement.prescription.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "prescriptionDate",
            "createdAt",
            "updatedAt"
    );

    private final PrescriptionService prescriptionService;
    private final PrescriptionPdfService prescriptionPdfService;

    public PrescriptionController(PrescriptionService prescriptionService, PrescriptionPdfService prescriptionPdfService) {
        this.prescriptionService = prescriptionService;
        this.prescriptionPdfService = prescriptionPdfService;
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

    @GetMapping("/api/v1/prescriptions/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Generate a prescription PDF")
    public ResponseEntity<ByteArrayResource> getPrescriptionPdf(@PathVariable UUID id) {
        byte[] pdf = prescriptionPdfService.generatePrescriptionPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(prescriptionPdfService.filenameFor(id))
                                .build()
                                .toString()
                )
                .body(new ByteArrayResource(pdf));
    }

    @GetMapping("/api/v1/patients/{patientId}/prescriptions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "List prescriptions for a patient")
    public Page<PrescriptionResponse> getPatientPrescriptions(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20, sort = "prescriptionDate") Pageable pageable
    ) {
        PageableSortValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return prescriptionService.getPatientPrescriptions(patientId, pageable);
    }
}
