package com.patientmanagement.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PrescriptionRequest(
        @NotNull
        UUID patientId,

        @NotNull
        UUID doctorId,

        @NotNull
        @PastOrPresent
        LocalDate prescriptionDate,

        @Size(max = 5000)
        String notes,

        @NotEmpty
        @Size(max = 50)
        List<@Valid PrescriptionItemRequest> items
) {
}
