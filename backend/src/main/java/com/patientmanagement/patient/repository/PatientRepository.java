package com.patientmanagement.patient.repository;

import com.patientmanagement.patient.model.Patient;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
}
