package com.patientmanagement.feedback.repository;

import com.patientmanagement.feedback.model.FeedbackAccessToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackAccessTokenRepository extends JpaRepository<FeedbackAccessToken, UUID> {

    @EntityGraph(attributePaths = {
            "consultation",
            "consultation.appointment",
            "consultation.appointment.patient",
            "consultation.appointment.doctor"
    })
    Optional<FeedbackAccessToken> findByTokenHash(String tokenHash);

    List<FeedbackAccessToken> findByConsultationIdAndRevokedAtIsNull(UUID consultationId);
}
