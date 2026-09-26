package com.patientmanagement.communication.model;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.patient.model.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "communications")
@EntityListeners(AuditingEntityListener.class)
public class Communication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CommunicationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommunicationChannel channel;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommunicationStatus status;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Communication() {
    }

    public Communication(
            Patient patient,
            Appointment appointment,
            CommunicationType type,
            CommunicationChannel channel,
            String recipient
    ) {
        this.patient = patient;
        this.appointment = appointment;
        this.type = type;
        this.channel = channel;
        this.recipient = recipient;
        this.status = CommunicationStatus.PENDING;
        this.attemptCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public CommunicationType getType() {
        return type;
    }

    public CommunicationChannel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public CommunicationStatus getStatus() {
        return status;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markSent(String providerMessageId, Instant sentAt) {
        this.attemptCount++;
        this.status = CommunicationStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.failureReason = null;
        this.sentAt = sentAt;
    }

    public void markSimulated() {
        this.status = CommunicationStatus.SIMULATED;
        this.attemptCount++;
    }

    public void markDisabled() {
        this.status = CommunicationStatus.DISABLED;
    }

    public void markFailed(String failureReason) {
        this.attemptCount++;
        this.status = CommunicationStatus.FAILED;
        this.failureReason = failureReason;
    }
}
