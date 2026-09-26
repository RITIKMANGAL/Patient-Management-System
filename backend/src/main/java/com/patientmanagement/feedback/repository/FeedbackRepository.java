package com.patientmanagement.feedback.repository;

import com.patientmanagement.feedback.model.Feedback;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    boolean existsByConsultationId(UUID consultationId);

    @EntityGraph(attributePaths = {
            "consultation",
            "consultation.appointment",
            "patient",
            "doctor"
    })
    Optional<Feedback> findByConsultationId(UUID consultationId);

    @Override
    @EntityGraph(attributePaths = {
            "consultation",
            "consultation.appointment",
            "patient",
            "doctor"
    })
    Page<Feedback> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "consultation",
            "consultation.appointment",
            "patient",
            "doctor"
    })
    Page<Feedback> findByDoctorId(UUID doctorId, Pageable pageable);
}
