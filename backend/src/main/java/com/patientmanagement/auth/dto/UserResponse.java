package com.patientmanagement.auth.dto;

import com.patientmanagement.auth.model.RoleName;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String firstName,
        String lastName,
        boolean enabled,
        Set<RoleName> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
