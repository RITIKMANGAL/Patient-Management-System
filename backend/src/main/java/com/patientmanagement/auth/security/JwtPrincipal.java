package com.patientmanagement.auth.security;

import com.patientmanagement.auth.model.RoleName;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;

public record JwtPrincipal(UUID userId, String username, Set<RoleName> roles) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
