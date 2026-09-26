package com.patientmanagement.appointment.dto;

import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentRequest(
        @NotNull
        UUID patientId,

        @NotNull
        UUID doctorId,

        @NotNull
        LocalDateTime appointmentDateTime,

        @NotBlank
        @Size(max = 500)
        @Pattern(regexp = ValidationPatterns.TEXT_WITH_LETTER, message = "must contain letters")
        String reason,

        AppointmentStatus status,

        @Size(max = 1000)
        String notes
) {
}
