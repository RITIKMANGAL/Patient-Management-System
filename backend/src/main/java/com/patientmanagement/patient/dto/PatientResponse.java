package com.patientmanagement.patient.dto;

import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.PatientGender;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        PatientGender gender,
        BloodGroup bloodGroup,
        String phone,
        String email,
        String address,
        String emergencyContactName,
        String emergencyContactPhone,
        Instant createdAt,
        Instant updatedAt
) {
}
