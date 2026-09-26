package com.patientmanagement.communication.repository;

import com.patientmanagement.communication.model.Communication;
import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationStatus;
import com.patientmanagement.communication.model.CommunicationType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationRepository extends JpaRepository<Communication, UUID> {

    @Override
    @EntityGraph(attributePaths = {"patient", "appointment"})
    Optional<Communication> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"patient", "appointment"})
    Page<Communication> findAll(Pageable pageable);

    boolean existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
            UUID patientId,
            UUID appointmentId,
            CommunicationType type,
            CommunicationChannel channel,
            Collection<CommunicationStatus> statuses
    );
}
