package com.patientmanagement.medicalrecord.model;

import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.patient.model.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "medical_records")
@EntityListeners(AuditingEntityListener.class)
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(nullable = false, length = 500)
    private String diagnosis;

    @Column(length = 1000)
    private String symptoms;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MedicalRecord() {
    }

    public MedicalRecord(
            Patient patient,
            Doctor doctor,
            String diagnosis,
            String symptoms,
            String notes,
            LocalDate recordDate
    ) {
        this.patient = patient;
        this.doctor = doctor;
        this.diagnosis = diagnosis;
        this.symptoms = symptoms;
        this.notes = notes;
        this.recordDate = recordDate;
    }

    public void update(
            Patient patient,
            Doctor doctor,
            String diagnosis,
            String symptoms,
            String notes,
            LocalDate recordDate
    ) {
        this.patient = patient;
        this.doctor = doctor;
        this.diagnosis = diagnosis;
        this.symptoms = symptoms;
        this.notes = notes;
        this.recordDate = recordDate;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
