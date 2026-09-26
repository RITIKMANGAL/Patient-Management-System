package com.patientmanagement.consultation.model;

import com.patientmanagement.appointment.model.Appointment;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "consultations")
@EntityListeners(AuditingEntityListener.class)
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsultationStatus status;

    @Column(name = "chief_complaint", length = 4000)
    private String chiefComplaint;

    @Column(name = "symptoms", length = 4000)
    private String symptoms;

    @Column(name = "examination", length = 4000)
    private String examination;

    @Column(name = "assessment", length = 4000)
    private String assessment;

    @Column(name = "treatment", length = 4000)
    private String treatment;

    @Column(name = "follow_up_instructions", length = 4000)
    private String followUpInstructions;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Consultation() {
    }

    public Consultation(Appointment appointment, Instant startedAt) {
        this.appointment = appointment;
        this.status = ConsultationStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    public void updateClinicalNotes(
            String chiefComplaint,
            String symptoms,
            String examination,
            String assessment,
            String treatment,
            String followUpInstructions
    ) {
        this.chiefComplaint = chiefComplaint;
        this.symptoms = symptoms;
        this.examination = examination;
        this.assessment = assessment;
        this.treatment = treatment;
        this.followUpInstructions = followUpInstructions;
    }

    public void complete(Instant completedAt) {
        this.status = ConsultationStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void reopen(Instant startedAt) {
        this.status = ConsultationStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.completedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public ConsultationStatus getStatus() {
        return status;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public String getExamination() {
        return examination;
    }

    public String getAssessment() {
        return assessment;
    }

    public String getTreatment() {
        return treatment;
    }

    public String getFollowUpInstructions() {
        return followUpInstructions;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
