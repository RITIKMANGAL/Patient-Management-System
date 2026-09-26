package com.patientmanagement.ai.controller;

import com.patientmanagement.ai.dto.AiConsultationDraftRequest;
import com.patientmanagement.ai.dto.AiConsultationDraftResponse;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;
import com.patientmanagement.ai.service.AiClinicalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "AI Clinical Assistant", description = "Assistive clinical documentation and patient history summary APIs")
public class AiClinicalController {

    private final AiClinicalService aiClinicalService;

    public AiClinicalController(AiClinicalService aiClinicalService) {
        this.aiClinicalService = aiClinicalService;
    }

    @PostMapping("/api/v1/consultations/{consultationId}/ai/draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Generate an AI-assisted consultation note draft")
    public AiConsultationDraftResponse generateConsultationDraft(
            @PathVariable UUID consultationId,
            @Valid @RequestBody AiConsultationDraftRequest request
    ) {
        return aiClinicalService.generateConsultationDraft(consultationId, request);
    }

    @GetMapping("/api/v1/patients/{patientId}/ai/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Generate an AI-assisted patient history summary")
    public AiPatientHistorySummaryResponse summarizePatientHistory(@PathVariable UUID patientId) {
        return aiClinicalService.summarizePatientHistory(patientId);
    }
}
