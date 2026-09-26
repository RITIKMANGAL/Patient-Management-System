package com.patientmanagement.prescription.repository;

import com.patientmanagement.prescription.model.Prescription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    @Override
    @EntityGraph(attributePaths = {"patient", "doctor", "items"})
    Optional<Prescription> findById(UUID id);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    Page<Prescription> findByPatientId(UUID patientId, Pageable pageable);

    Optional<Prescription> findFirstByNotesContaining(String marker);
}
