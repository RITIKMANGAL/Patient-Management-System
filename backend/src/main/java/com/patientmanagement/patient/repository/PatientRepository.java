package com.patientmanagement.patient.repository;

import com.patientmanagement.patient.model.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByEmailIgnoreCase(String email);

    @Query("""
            SELECT patient
            FROM Patient patient
            WHERE patient.id = :patientId
              AND EXISTS (
                  SELECT 1
                  FROM Appointment appointment
                  WHERE appointment.patient = patient
                    AND appointment.doctor.id = :doctorId
              )
            """)
    Optional<Patient> findByIdForDoctor(
            @Param("patientId") UUID patientId,
            @Param("doctorId") UUID doctorId
    );

    @Query(
            value = """
                    SELECT patient
                    FROM Patient patient
                    WHERE EXISTS (
                        SELECT 1
                        FROM Appointment appointment
                        WHERE appointment.patient = patient
                          AND appointment.doctor.id = :doctorId
                    )
                    """,
            countQuery = """
                    SELECT COUNT(patient)
                    FROM Patient patient
                    WHERE EXISTS (
                        SELECT 1
                        FROM Appointment appointment
                        WHERE appointment.patient = patient
                          AND appointment.doctor.id = :doctorId
                    )
                    """
    )
    Page<Patient> findPatientsForDoctor(@Param("doctorId") UUID doctorId, Pageable pageable);
}
