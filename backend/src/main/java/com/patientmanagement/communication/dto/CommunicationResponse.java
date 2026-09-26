package com.patientmanagement.communication.dto;

import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationStatus;
import com.patientmanagement.communication.model.CommunicationType;
import java.time.Instant;
import java.util.UUID;

public record CommunicationResponse(
        UUID id,
        UUID patientId,
        String patientName,
        UUID appointmentId,
        CommunicationType type,
        CommunicationChannel channel,
        String recipient,
        CommunicationStatus status,
        String providerMessageId,
        String failureReason,
        int attemptCount,
        Instant sentAt,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt
) {
}
