package com.patientmanagement.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.repository.DoctorRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClinicalAccessServiceTests {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    private ClinicalAccessService clinicalAccessService;
    private UUID userId;
    private UUID doctorId;

    @BeforeEach
    void setUp() {
        clinicalAccessService = new ClinicalAccessService(
                new CurrentUserService(),
                doctorRepository,
                appointmentRepository
        );
        userId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesScopedDoctorFromJwtUserId() {
        authenticateAsDoctor();
        Doctor doctor = new Doctor("Kiran", "Shah", "Cardiology", "LIC-100", "+15555550200", null, null);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
        when(doctorRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(doctor));

        assertThat(clinicalAccessService.scopedDoctorId()).contains(doctorId);
    }

    @Test
    void adminDoesNotRequireDoctorScope() {
        authenticate(RoleName.ADMIN);

        assertThat(clinicalAccessService.scopedDoctorId()).isEmpty();
    }

    @Test
    void doctorCannotUseAnotherDoctorId() {
        authenticateAsDoctor();
        Doctor doctor = new Doctor("Kiran", "Shah", "Cardiology", "LIC-100", "+15555550200", null, null);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
        when(doctorRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(doctor));

        assertThatThrownBy(() -> clinicalAccessService.requireDoctorMatches(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Doctor not found");
    }

    @Test
    void doctorCannotAccessUnrelatedPatient() {
        authenticateAsDoctor();
        Doctor doctor = new Doctor("Kiran", "Shah", "Cardiology", "LIC-100", "+15555550200", null, null);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
        UUID patientId = UUID.randomUUID();
        when(doctorRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(doctor));
        when(appointmentRepository.existsByPatientIdAndDoctorId(patientId, doctorId)).thenReturn(false);

        assertThatThrownBy(() -> clinicalAccessService.requirePatientAccess(patientId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    private void authenticateAsDoctor() {
        authenticate(RoleName.DOCTOR);
    }

    private void authenticate(RoleName roleName) {
        JwtPrincipal principal = new JwtPrincipal(userId, "doctor@example.com", Set.of(roleName));
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName.name()))
        );
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
