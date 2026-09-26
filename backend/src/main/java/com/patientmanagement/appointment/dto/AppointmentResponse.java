package com.patientmanagement.appointment.dto;

import com.patientmanagement.appointment.model.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID patientId,
        String patientName,
        UUID doctorId,
        String doctorName,
        LocalDateTime appointmentDateTime,
        String reason,
        AppointmentStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
