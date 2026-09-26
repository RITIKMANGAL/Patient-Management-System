package com.patientmanagement.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.patient.dto.PatientRequest;
import com.patientmanagement.patient.dto.PatientResponse;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.repository.PatientRepository;
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
class PatientServiceTests {

    @Mock
    private PatientRepository patientRepository;

    private PatientService patientService;

    @BeforeEach
    void setUp() {
        patientService = new PatientService(patientRepository);
    }

    @Test
    void createPatientReturnsCreatedPatient() {
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientResponse response = patientService.createPatient(patientRequest("Asha", "Rao"));

        assertThat(response.firstName()).isEqualTo("Asha");
        assertThat(response.lastName()).isEqualTo("Rao");
        assertThat(response.gender()).isEqualTo(PatientGender.FEMALE);
    }

    @Test
    void getPatientReturnsExistingPatient() {
        UUID id = UUID.randomUUID();
        Patient patient = patient("Asha", "Rao");
        ReflectionTestUtils.setField(patient, "id", id);
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));

        PatientResponse response = patientService.getPatient(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.firstName()).isEqualTo("Asha");
    }

    @Test
    void getPatientsReturnsPagedPatients() {
        when(patientRepository.findAll(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(java.util.List.of(patient("Asha", "Rao"))));

        assertThat(patientService.getPatients(PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void doctorScopedPatientListReturnsOnlyPatientsWithDoctorRelationship() {
        UUID doctorId = UUID.randomUUID();
        ClinicalAccessService clinicalAccessService = org.mockito.Mockito.mock(ClinicalAccessService.class);
        PatientService scopedService = new PatientService(patientRepository, clinicalAccessService);
        when(clinicalAccessService.scopedDoctorId()).thenReturn(Optional.of(doctorId));
        when(clinicalAccessService.currentDoctorId()).thenReturn(doctorId);
        when(patientRepository.findPatientsForDoctor(doctorId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(java.util.List.of(patient("Asha", "Rao"))));

        assertThat(scopedService.getPatients(PageRequest.of(0, 20)).getContent())
                .extracting(PatientResponse::firstName)
                .containsExactly("Asha");
    }

    @Test
    void updatePatientUpdatesExistingPatient() {
        UUID id = UUID.randomUUID();
        Patient patient = patient("Asha", "Rao");
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));

        PatientResponse response = patientService.updatePatient(id, patientRequest("Meera", "Rao"));

        assertThat(response.firstName()).isEqualTo("Meera");
        assertThat(patient.getFirstName()).isEqualTo("Meera");
    }

    @Test
    void deletePatientDeletesExistingPatient() {
        UUID id = UUID.randomUUID();
        Patient patient = patient("Asha", "Rao");
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));

        patientService.deletePatient(id);

        verify(patientRepository).delete(patient);
    }

    @Test
    void getPatientThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(patientRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatient(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    private PatientRequest patientRequest(String firstName, String lastName) {
        return new PatientRequest(
                firstName,
                lastName,
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

    private Patient patient(String firstName, String lastName) {
        return new Patient(
                firstName,
                lastName,
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
}
