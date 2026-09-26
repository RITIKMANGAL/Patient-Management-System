package com.patientmanagement.doctor.repository;

import com.patientmanagement.doctor.model.Doctor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select doctor from Doctor doctor where doctor.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") UUID id);

    Optional<Doctor> findByLicenseNumber(String licenseNumber);

    Optional<Doctor> findByUserId(UUID userId);

    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumberAndIdNot(String licenseNumber, UUID id);
}
