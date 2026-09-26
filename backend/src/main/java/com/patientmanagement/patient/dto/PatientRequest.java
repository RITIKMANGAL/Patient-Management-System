package com.patientmanagement.patient.dto;

import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PatientRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = "must be a valid name")
        String firstName,

        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = "must be a valid name")
        String lastName,

        @NotNull
        @Past
        LocalDate dateOfBirth,

        @NotNull
        PatientGender gender,

        BloodGroup bloodGroup,

        @NotBlank
        @Pattern(regexp = ValidationPatterns.PHONE, message = "must be a valid phone number")
        String phone,

        @Email
        @Size(max = 255)
        String email,

        @Size(max = 500)
        String address,

        @Size(max = 150)
        @Pattern(regexp = "^$|" + ValidationPatterns.PERSON_NAME, message = "must be a valid name")
        String emergencyContactName,

        @Pattern(regexp = "^$|" + ValidationPatterns.PHONE, message = "must be a valid phone number")
        String emergencyContactPhone
) {
}
