package com.patientmanagement.prescription.repository;

import com.patientmanagement.prescription.model.Prescription;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    Page<Prescription> findByPatientId(UUID patientId, Pageable pageable);
}
