package com.patientmanagement.medicalrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.medicalrecord.dto.MedicalRecordRequest;
import com.patientmanagement.medicalrecord.dto.MedicalRecordResponse;
import com.patientmanagement.medicalrecord.model.MedicalRecord;
import com.patientmanagement.medicalrecord.repository.MedicalRecordRepository;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.service.PatientService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTests {

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private PatientService patientService;

    @Mock
    private DoctorService doctorService;

    private MedicalRecordService medicalRecordService;
    private UUID patientId;
    private UUID doctorId;
    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        medicalRecordService = new MedicalRecordService(medicalRecordRepository, patientService, doctorService);
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        patient = patient();
        doctor = doctor();
        ReflectionTestUtils.setField(patient, "id", patientId);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
    }

    @Test
    void createMedicalRecordReturnsCreatedRecord() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenReturn(doctor);
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalRecordResponse response = medicalRecordService.createMedicalRecord(request(patientId, doctorId));

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.doctorId()).isEqualTo(doctorId);
        assertThat(response.diagnosis()).isEqualTo("Synthetic diagnosis");
    }

    @Test
    void getMedicalRecordReturnsExistingRecord() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord record = record();
        ReflectionTestUtils.setField(record, "id", recordId);
        when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));

        MedicalRecordResponse response = medicalRecordService.getMedicalRecord(recordId);

        assertThat(response.id()).isEqualTo(recordId);
        assertThat(response.patientName()).isEqualTo("Asha Rao");
    }

    @Test
    void getPatientMedicalRecordsReturnsPatientRecords() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(medicalRecordRepository.findByPatientId(patientId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(java.util.List.of(record())));

        assertThat(medicalRecordService.getPatientMedicalRecords(patientId, PageRequest.of(0, 20)).getContent())
                .hasSize(1);
    }

    @Test
    void createMedicalRecordRejectsInvalidPatient() {
        when(patientService.findPatientEntity(patientId)).thenThrow(new ResourceNotFoundException("Patient not found"));

        assertThatThrownBy(() -> medicalRecordService.createMedicalRecord(request(patientId, doctorId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    @Test
    void createMedicalRecordRejectsInvalidDoctor() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenThrow(new ResourceNotFoundException("Doctor not found"));

        assertThatThrownBy(() -> medicalRecordService.createMedicalRecord(request(patientId, doctorId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Doctor not found");
    }

    private MedicalRecordRequest request(UUID patientId, UUID doctorId) {
        return new MedicalRecordRequest(
                patientId,
                doctorId,
                "Synthetic diagnosis",
                "Synthetic symptoms",
                "Synthetic notes",
                LocalDate.now()
        );
    }

    private MedicalRecord record() {
        return new MedicalRecord(
                patient,
                doctor,
                "Synthetic diagnosis",
                "Synthetic symptoms",
                "Synthetic notes",
                LocalDate.now()
        );
    }

    private Patient patient() {
        return new Patient(
                "Asha",
                "Rao",
                LocalDate.of(1990, 1, 1),
                PatientGender.FEMALE,
                BloodGroup.O_POSITIVE,
                "+15555550100",
                "synthetic.patient@example.com",
                "Synthetic address",
                "Synthetic Contact",
                "+15555550101"
        );
    }

    private Doctor doctor() {
        return new Doctor(
                "Kiran",
                "Shah",
                "Cardiology",
                "LIC-100",
                "+15555550200",
                "synthetic.doctor@example.com",
                "Cardiology"
        );
    }
}
