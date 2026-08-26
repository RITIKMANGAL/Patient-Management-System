package com.patientmanagement.doctor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.dto.DoctorRequest;
import com.patientmanagement.doctor.dto.DoctorResponse;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.repository.DoctorRepository;
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
class DoctorServiceTests {

    @Mock
    private DoctorRepository doctorRepository;

    private DoctorService doctorService;

    @BeforeEach
    void setUp() {
        doctorService = new DoctorService(doctorRepository);
    }

    @Test
    void createDoctorReturnsCreatedDoctor() {
        when(doctorRepository.existsByLicenseNumber("LIC-100")).thenReturn(false);
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DoctorResponse response = doctorService.createDoctor(doctorRequest("LIC-100"));

        assertThat(response.firstName()).isEqualTo("Kiran");
        assertThat(response.licenseNumber()).isEqualTo("LIC-100");
    }

    @Test
    void createDoctorRejectsDuplicateLicenseNumber() {
        when(doctorRepository.existsByLicenseNumber("LIC-100")).thenReturn(true);

        assertThatThrownBy(() -> doctorService.createDoctor(doctorRequest("LIC-100")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Doctor license number already exists");
    }

    @Test
    void getDoctorReturnsExistingDoctor() {
        UUID id = UUID.randomUUID();
        Doctor doctor = doctor("LIC-100");
        ReflectionTestUtils.setField(doctor, "id", id);
        when(doctorRepository.findById(id)).thenReturn(Optional.of(doctor));

        DoctorResponse response = doctorService.getDoctor(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.licenseNumber()).isEqualTo("LIC-100");
    }

    @Test
    void getDoctorsReturnsPagedDoctors() {
        when(doctorRepository.findAll(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(java.util.List.of(doctor("LIC-100"))));

        assertThat(doctorService.getDoctors(PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void updateDoctorUpdatesExistingDoctor() {
        UUID id = UUID.randomUUID();
        Doctor doctor = doctor("LIC-100");
        when(doctorRepository.existsByLicenseNumberAndIdNot("LIC-101", id)).thenReturn(false);
        when(doctorRepository.findById(id)).thenReturn(Optional.of(doctor));

        DoctorResponse response = doctorService.updateDoctor(id, doctorRequest("LIC-101"));

        assertThat(response.licenseNumber()).isEqualTo("LIC-101");
        assertThat(doctor.getLicenseNumber()).isEqualTo("LIC-101");
    }

    @Test
    void deleteDoctorDeletesExistingDoctor() {
        UUID id = UUID.randomUUID();
        Doctor doctor = doctor("LIC-100");
        when(doctorRepository.findById(id)).thenReturn(Optional.of(doctor));

        doctorService.deleteDoctor(id);

        verify(doctorRepository).delete(doctor);
    }

    @Test
    void getDoctorThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(doctorRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.getDoctor(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Doctor not found");
    }

    private DoctorRequest doctorRequest(String licenseNumber) {
        return new DoctorRequest(
                "Kiran",
                "Shah",
                "Cardiology",
                licenseNumber,
                "+15555550200",
                "synthetic.doctor@example.com",
                "Cardiology"
        );
    }

    private Doctor doctor(String licenseNumber) {
        return new Doctor(
                "Kiran",
                "Shah",
                "Cardiology",
                licenseNumber,
                "+15555550200",
                "synthetic.doctor@example.com",
                "Cardiology"
        );
    }
}
