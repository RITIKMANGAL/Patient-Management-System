package com.patientmanagement.auth.repository;

import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    Optional<AuthUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRolesName(RoleName name);
}
