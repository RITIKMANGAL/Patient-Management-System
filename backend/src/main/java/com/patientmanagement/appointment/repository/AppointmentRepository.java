package com.patientmanagement.appointment.repository;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    @Override
    @EntityGraph(attributePaths = {"patient", "doctor"})
    Optional<Appointment> findById(UUID id);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    Optional<Appointment> findByIdAndDoctorId(UUID id, UUID doctorId);

    @Override
    @EntityGraph(attributePaths = {"patient", "doctor"})
    Page<Appointment> findAll(Specification<Appointment> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusIn(
            LocalDateTime from,
            LocalDateTime to,
            Collection<AppointmentStatus> statuses
    );

    @EntityGraph(attributePaths = {"patient", "doctor"})
    Optional<Appointment> findFirstByNotesContaining(String marker);

    boolean existsByPatientIdAndDoctorId(UUID patientId, UUID doctorId);

    @Query("""
            SELECT COUNT(appointment) > 0
            FROM Appointment appointment
            WHERE appointment.doctor.id = :doctorId
              AND appointment.status IN :activeStatuses
              AND appointment.appointmentDateTime > :windowStart
              AND appointment.appointmentDateTime < :windowEnd
              AND (:excludedAppointmentId IS NULL OR appointment.id <> :excludedAppointmentId)
            """)
    boolean existsOverlappingDoctorAppointment(
            @Param("doctorId") UUID doctorId,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd,
            @Param("activeStatuses") Collection<AppointmentStatus> activeStatuses,
            @Param("excludedAppointmentId") UUID excludedAppointmentId
    );

    @Query("""
            SELECT COUNT(appointment) > 0
            FROM Appointment appointment
            WHERE appointment.patient.id = :patientId
              AND appointment.status IN :activeStatuses
              AND appointment.appointmentDateTime > :windowStart
              AND appointment.appointmentDateTime < :windowEnd
              AND (:excludedAppointmentId IS NULL OR appointment.id <> :excludedAppointmentId)
            """)
    boolean existsOverlappingPatientAppointment(
            @Param("patientId") UUID patientId,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd,
            @Param("activeStatuses") Collection<AppointmentStatus> activeStatuses,
            @Param("excludedAppointmentId") UUID excludedAppointmentId
    );
}
