package com.patientmanagement.consultation.controller;

import com.patientmanagement.common.web.PageableSortValidator;
import com.patientmanagement.consultation.dto.ConsultationResponse;
import com.patientmanagement.consultation.dto.ConsultationUpdateRequest;
import com.patientmanagement.consultation.service.ConsultationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Consultations", description = "Clinical consultation APIs")
public class ConsultationController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "startedAt",
            "completedAt",
            "createdAt",
            "updatedAt",
            "status"
    );

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @PostMapping("/api/v1/appointments/{appointmentId}/consultation")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Start a consultation for an appointment")
    public ResponseEntity<ConsultationResponse> startConsultation(@PathVariable UUID appointmentId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consultationService.startConsultation(appointmentId));
    }

    @GetMapping("/api/v1/appointments/{appointmentId}/consultation")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Get the consultation for an appointment")
    public ConsultationResponse getAppointmentConsultation(@PathVariable UUID appointmentId) {
        return consultationService.getAppointmentConsultation(appointmentId);
    }

    @GetMapping("/api/v1/consultations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Get a consultation by ID")
    public ConsultationResponse getConsultation(@PathVariable UUID id) {
        return consultationService.getConsultation(id);
    }

    @PutMapping("/api/v1/consultations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Update an in-progress consultation")
    public ConsultationResponse updateConsultation(
            @PathVariable UUID id,
            @Valid @RequestBody ConsultationUpdateRequest request
    ) {
        return consultationService.updateConsultation(id, request);
    }

    @PostMapping("/api/v1/consultations/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Complete a consultation")
    public ConsultationResponse completeConsultation(@PathVariable UUID id) {
        return consultationService.completeConsultation(id);
    }

    @GetMapping("/api/v1/patients/{patientId}/consultations")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "List consultations for a patient")
    public Page<ConsultationResponse> getPatientConsultations(
            @PathVariable UUID patientId,
            @PageableDefault(sort = "startedAt", direction = Direction.DESC) Pageable pageable
    ) {
        PageableSortValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return consultationService.getPatientConsultations(patientId, pageable);
    }
}
