package com.patientmanagement.consultation.dto;

import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.consultation.model.ConsultationStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConsultationResponse(
        UUID id,
        UUID appointmentId,
        UUID patientId,
        String patientName,
        UUID doctorId,
        String doctorName,
        LocalDateTime appointmentDateTime,
        AppointmentStatus appointmentStatus,
        ConsultationStatus status,
        String chiefComplaint,
        String symptoms,
        String examination,
        String assessment,
        String treatment,
        String followUpInstructions,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
