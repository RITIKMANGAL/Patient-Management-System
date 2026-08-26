package com.patientmanagement.medicalrecord.controller;

import com.patientmanagement.medicalrecord.dto.MedicalRecordRequest;
import com.patientmanagement.medicalrecord.dto.MedicalRecordResponse;
import com.patientmanagement.medicalrecord.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Medical Records", description = "Core medical record APIs")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @PostMapping("/api/v1/medical-records")
    @Operation(summary = "Create a medical record")
    public ResponseEntity<MedicalRecordResponse> createMedicalRecord(
            @Valid @RequestBody MedicalRecordRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicalRecordService.createMedicalRecord(request));
    }

    @GetMapping("/api/v1/medical-records/{id}")
    @Operation(summary = "Get a medical record by ID")
    public MedicalRecordResponse getMedicalRecord(@PathVariable UUID id) {
        return medicalRecordService.getMedicalRecord(id);
    }

    @GetMapping("/api/v1/patients/{patientId}/medical-records")
    @Operation(summary = "List medical records for a patient")
    public Page<MedicalRecordResponse> getPatientMedicalRecords(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20, sort = "recordDate") Pageable pageable
    ) {
        return medicalRecordService.getPatientMedicalRecords(patientId, pageable);
    }
}
