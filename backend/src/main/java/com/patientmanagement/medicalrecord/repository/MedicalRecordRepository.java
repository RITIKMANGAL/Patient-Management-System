package com.patientmanagement.medicalrecord.repository;

import com.patientmanagement.medicalrecord.model.MedicalRecord;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Page<MedicalRecord> findByPatientId(UUID patientId, Pageable pageable);
}
