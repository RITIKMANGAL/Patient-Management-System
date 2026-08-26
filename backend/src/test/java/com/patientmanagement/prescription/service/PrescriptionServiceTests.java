package com.patientmanagement.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.dto.PrescriptionItemRequest;
import com.patientmanagement.prescription.dto.PrescriptionRequest;
import com.patientmanagement.prescription.dto.PrescriptionResponse;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.model.PrescriptionItem;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import java.time.LocalDate;
import java.util.List;
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
class PrescriptionServiceTests {

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private PatientService patientService;

    @Mock
    private DoctorService doctorService;

    private PrescriptionService prescriptionService;
    private UUID patientId;
    private UUID doctorId;
    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        prescriptionService = new PrescriptionService(prescriptionRepository, patientService, doctorService);
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        patient = patient();
        doctor = doctor();
        ReflectionTestUtils.setField(patient, "id", patientId);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
    }

    @Test
    void createPrescriptionWithItemsReturnsCreatedPrescription() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenReturn(doctor);
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrescriptionResponse response = prescriptionService.createPrescription(request(patientId, doctorId));

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.doctorId()).isEqualTo(doctorId);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().medicineName()).isEqualTo("Synthetic medicine A");
    }

    @Test
    void getPrescriptionReturnsExistingPrescription() {
        UUID prescriptionId = UUID.randomUUID();
        Prescription prescription = prescription();
        ReflectionTestUtils.setField(prescription, "id", prescriptionId);
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));

        PrescriptionResponse response = prescriptionService.getPrescription(prescriptionId);

        assertThat(response.id()).isEqualTo(prescriptionId);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void getPatientPrescriptionsReturnsPatientPrescriptions() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(prescriptionRepository.findByPatientId(patientId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(prescription())));

        assertThat(prescriptionService.getPatientPrescriptions(patientId, PageRequest.of(0, 20)).getContent())
                .hasSize(1);
    }

    @Test
    void createPrescriptionRejectsInvalidPatient() {
        when(patientService.findPatientEntity(patientId)).thenThrow(new ResourceNotFoundException("Patient not found"));

        assertThatThrownBy(() -> prescriptionService.createPrescription(request(patientId, doctorId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    @Test
    void createPrescriptionRejectsInvalidDoctor() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenThrow(new ResourceNotFoundException("Doctor not found"));

        assertThatThrownBy(() -> prescriptionService.createPrescription(request(patientId, doctorId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Doctor not found");
    }

    private PrescriptionRequest request(UUID patientId, UUID doctorId) {
        return new PrescriptionRequest(
                patientId,
                doctorId,
                LocalDate.now(),
                "Synthetic notes",
                List.of(
                        new PrescriptionItemRequest("Synthetic medicine A", "10mg", "Once daily", "5 days", "After food"),
                        new PrescriptionItemRequest("Synthetic medicine B", "5ml", "Twice daily", "3 days", "With water")
                )
        );
    }

    private Prescription prescription() {
        Prescription prescription = new Prescription(patient, doctor, LocalDate.now(), "Synthetic notes");
        prescription.addItem(new PrescriptionItem("Synthetic medicine A", "10mg", "Once daily", "5 days", "After food"));
        return prescription;
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
