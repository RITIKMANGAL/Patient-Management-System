package com.patientmanagement.auth.repository;

import com.patientmanagement.auth.model.Role;
import com.patientmanagement.auth.model.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);
}
