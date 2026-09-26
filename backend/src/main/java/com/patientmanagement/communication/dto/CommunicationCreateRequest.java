package com.patientmanagement.communication.dto;

import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CommunicationCreateRequest(
        @NotNull
        UUID patientId,

        UUID appointmentId,

        @NotNull
        CommunicationType type,

        @NotNull
        CommunicationChannel channel,

        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^(?=(?:.*\\d){7,})\\+?[0-9 .()\\-]{7,25}$", message = "must be a valid phone number")
        String recipient
) {
}
