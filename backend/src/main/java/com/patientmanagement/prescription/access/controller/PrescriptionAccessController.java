package com.patientmanagement.prescription.access.controller;

import com.patientmanagement.prescription.access.dto.PrescriptionAccessTokenResponse;
import com.patientmanagement.prescription.access.dto.PrescriptionAccessStatusResponse;
import com.patientmanagement.prescription.access.service.PrescriptionAccessTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Prescription Access", description = "Secure prescription-specific patient access APIs")
public class PrescriptionAccessController {

    private final PrescriptionAccessTokenService accessTokenService;

    public PrescriptionAccessController(PrescriptionAccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    @PostMapping("/api/v1/prescriptions/{prescriptionId}/access")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Create a secure patient access token for a prescription")
    public PrescriptionAccessTokenResponse createPrescriptionAccess(@PathVariable UUID prescriptionId) {
        return accessTokenService.createAccessToken(prescriptionId);
    }

    @GetMapping("/api/v1/prescriptions/{prescriptionId}/access")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Check whether a prescription has an active patient access token")
    public PrescriptionAccessStatusResponse getPrescriptionAccessStatus(@PathVariable UUID prescriptionId) {
        return accessTokenService.getActiveAccessStatus(prescriptionId);
    }

    @DeleteMapping("/api/v1/prescriptions/{prescriptionId}/access")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Revoke active patient access token for a prescription")
    public ResponseEntity<Void> revokePrescriptionAccess(@PathVariable UUID prescriptionId) {
        accessTokenService.revokeActiveAccessToken(prescriptionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/prescription-access/{token}/pdf")
    @Operation(summary = "Download a prescription PDF using a secure patient access token")
    public ResponseEntity<ByteArrayResource> getPrescriptionPdfByAccessToken(@PathVariable String token) {
        byte[] pdf = accessTokenService.generatePrescriptionPdf(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("prescription.pdf")
                                .build()
                                .toString()
                )
                .body(new ByteArrayResource(pdf));
    }
}
