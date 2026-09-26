package com.patientmanagement.feedback.controller;

import com.patientmanagement.common.web.PageableSortValidator;
import com.patientmanagement.feedback.dto.FeedbackAccessTokenResponse;
import com.patientmanagement.feedback.dto.FeedbackContextResponse;
import com.patientmanagement.feedback.dto.FeedbackResponse;
import com.patientmanagement.feedback.dto.FeedbackSubmitRequest;
import com.patientmanagement.feedback.service.FeedbackService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Feedback", description = "Patient feedback APIs")
public class FeedbackController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "rating"
    );

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/api/v1/consultations/{consultationId}/feedback-access")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Create a secure patient feedback access token for a completed consultation")
    public FeedbackAccessTokenResponse createFeedbackAccess(@PathVariable UUID consultationId) {
        return feedbackService.createFeedbackAccessToken(consultationId);
    }

    @DeleteMapping("/api/v1/consultations/{consultationId}/feedback-access")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Revoke active patient feedback access token for a consultation")
    public ResponseEntity<Void> revokeFeedbackAccess(@PathVariable UUID consultationId) {
        feedbackService.revokeActiveFeedbackAccessToken(consultationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/feedback-access/{token}")
    @Operation(summary = "Get public feedback context using a secure feedback token")
    public FeedbackContextResponse getFeedbackContext(@PathVariable String token) {
        return feedbackService.getFeedbackContext(token);
    }

    @PostMapping("/api/v1/feedback-access/{token}")
    @Operation(summary = "Submit patient feedback using a secure feedback token")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @PathVariable String token,
            @Valid @RequestBody FeedbackSubmitRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submitFeedback(token, request));
    }

    @GetMapping("/api/v1/feedback")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "List submitted patient feedback")
    public Page<FeedbackResponse> getFeedback(
            @PageableDefault(sort = "createdAt", direction = Direction.DESC) Pageable pageable
    ) {
        PageableSortValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return feedbackService.getFeedback(pageable);
    }
}
