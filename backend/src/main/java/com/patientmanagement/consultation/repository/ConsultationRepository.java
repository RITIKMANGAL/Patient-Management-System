package com.patientmanagement.consultation.repository;

import com.patientmanagement.consultation.model.Consultation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    @Override
    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Optional<Consultation> findById(UUID id);

    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Optional<Consultation> findByIdAndAppointmentDoctorId(UUID id, UUID doctorId);

    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Optional<Consultation> findByAppointmentId(UUID appointmentId);

    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Optional<Consultation> findByAppointmentIdAndAppointmentDoctorId(UUID appointmentId, UUID doctorId);

    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Page<Consultation> findByAppointmentPatientId(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Page<Consultation> findByAppointmentPatientIdAndAppointmentDoctorId(UUID patientId, UUID doctorId, Pageable pageable);

    boolean existsByAppointmentId(UUID appointmentId);
}
