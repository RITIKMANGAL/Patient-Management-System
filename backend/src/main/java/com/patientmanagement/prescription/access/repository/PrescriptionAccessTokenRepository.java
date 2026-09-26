package com.patientmanagement.prescription.access.repository;

import com.patientmanagement.prescription.access.model.PrescriptionAccessToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionAccessTokenRepository extends JpaRepository<PrescriptionAccessToken, UUID> {

    @EntityGraph(attributePaths = {"prescription"})
    Optional<PrescriptionAccessToken> findByTokenHash(String tokenHash);

    List<PrescriptionAccessToken> findByPrescriptionIdAndRevokedAtIsNull(UUID prescriptionId);
}
