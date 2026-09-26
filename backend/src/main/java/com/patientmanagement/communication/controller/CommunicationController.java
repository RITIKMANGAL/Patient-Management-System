package com.patientmanagement.communication.controller;

import com.patientmanagement.common.web.PageableSortValidator;
import com.patientmanagement.communication.dto.CommunicationResponse;
import com.patientmanagement.communication.service.CommunicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communications")
@Tag(name = "Communications", description = "Patient communication audit APIs")
public class CommunicationController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "type",
            "channel",
            "status",
            "sentAt",
            "deliveredAt"
    );

    private final CommunicationService communicationService;

    public CommunicationController(CommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List patient communication records")
    public Page<CommunicationResponse> getCommunications(
            @PageableDefault(sort = "createdAt", direction = Direction.DESC) Pageable pageable
    ) {
        PageableSortValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return communicationService.getCommunications(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a patient communication record")
    public CommunicationResponse getCommunication(@PathVariable UUID id) {
        return communicationService.getCommunication(id);
    }
}
