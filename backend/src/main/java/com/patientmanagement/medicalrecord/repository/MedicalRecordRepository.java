package com.patientmanagement.medicalrecord.repository;

import com.patientmanagement.medicalrecord.model.MedicalRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    @Override
    @EntityGraph(attributePaths = {"patient", "doctor"})
    Optional<MedicalRecord> findById(UUID id);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    Page<MedicalRecord> findByPatientId(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    Optional<MedicalRecord> findFirstByNotesContaining(String marker);
}
