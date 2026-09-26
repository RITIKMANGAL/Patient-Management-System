package com.patientmanagement.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.patientmanagement.appointment.dto.AppointmentRequest;
import com.patientmanagement.appointment.dto.AppointmentResponse;
import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.service.PatientService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-27T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientService patientService;

    @Mock
    private DoctorService doctorService;

    @Mock
    private CommunicationService communicationService;

    private AppointmentService appointmentService;

    @Test
    void rejectsNonexistentAndAmbiguousClinicWallTimesBeforeRepositoryAccess() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneId.of("America/New_York"));
        var service = new AppointmentService(appointmentRepository, patientService, doctorService, communicationService, clock);
        for (var time : java.util.List.of(LocalDateTime.parse("2026-03-08T02:30:00"),
                LocalDateTime.parse("2026-11-01T01:30:00"))) {
            assertThatThrownBy(() -> service.createAppointment(new AppointmentRequest(
                    UUID.randomUUID(), UUID.randomUUID(), time, "Synthetic visit", null, null)))
                    .isInstanceOf(InvalidRequestException.class).hasMessageContaining("clinic timezone");
        }
        verifyNoInteractions(appointmentRepository, patientService, doctorService, communicationService);
    }
    private UUID patientId;
    private UUID doctorId;
    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepository,
                patientService,
                doctorService,
                communicationService,
                FIXED_CLOCK
        );
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        patient = patient();
        doctor = doctor();
        ReflectionTestUtils.setField(patient, "id", patientId);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
    }

    @Test
    void createAppointmentReturnsScheduledAppointment() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenReturn(doctor);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse response = appointmentService.createAppointment(request(AppointmentStatus.SCHEDULED));

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.doctorId()).isEqualTo(doctorId);
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.reason()).isEqualTo("Annual checkup");

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(communicationService).createAppointmentConfirmation(appointmentCaptor.capture());
        assertThat(appointmentCaptor.getValue().getPatient()).isSameAs(patient);
        assertThat(appointmentCaptor.getValue().getDoctor()).isSameAs(doctor);
        assertThat(appointmentCaptor.getValue().getAppointmentDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
    }

    @Test
    void createAppointmentDoesNotFailWhenConfirmationCommunicationFails() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenReturn(doctor);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("provider unavailable"))
                .when(communicationService).createAppointmentConfirmation(any(Appointment.class));

        AppointmentResponse response = appointmentService.createAppointment(request(AppointmentStatus.SCHEDULED));

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.doctorId()).isEqualTo(doctorId);
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(communicationService).createAppointmentConfirmation(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsMissingPatient() {
        when(patientService.findPatientEntity(patientId)).thenThrow(new ResourceNotFoundException("Patient not found"));

        assertThatThrownBy(() -> appointmentService.createAppointment(request(AppointmentStatus.SCHEDULED)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    @Test
    void createAppointmentRejectsMissingDoctor() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenThrow(new ResourceNotFoundException("Doctor not found"));

        assertThatThrownBy(() -> appointmentService.createAppointment(request(AppointmentStatus.SCHEDULED)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Doctor not found");
    }

    @Test
    void createAppointmentRejectsPastDateTime() {
        AppointmentRequest request = new AppointmentRequest(
                patientId,
                doctorId,
                LocalDateTime.of(2026, 8, 26, 9, 0),
                "Annual checkup",
                AppointmentStatus.SCHEDULED,
                "Bring reports"
        );

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Appointment date and time must not be in the past");
        verifyNoInteractions(communicationService);
    }

    @Test
    void createAppointmentRejectsOverlappingDoctorAppointment() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenReturn(doctor);
        when(appointmentRepository.existsOverlappingDoctorAppointment(eq(doctorId), any(), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> appointmentService.createAppointment(request(AppointmentStatus.CONFIRMED)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Doctor already has an appointment during this time slot");
    }

    @Test
    void createAppointmentRejectsOverlappingPatientAppointment() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenReturn(doctor);
        when(appointmentRepository.existsOverlappingPatientAppointment(eq(patientId), any(), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> appointmentService.createAppointment(request(AppointmentStatus.CONFIRMED)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Patient already has an appointment during this time slot");
    }

    @Test
    void getAppointmentReturnsExistingAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointment(AppointmentStatus.SCHEDULED);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        AppointmentResponse response = appointmentService.getAppointment(appointmentId);

        assertThat(response.id()).isEqualTo(appointmentId);
        assertThat(response.patientName()).isEqualTo("Asha Rao");
        assertThat(response.doctorName()).isEqualTo("Kiran Shah");
    }

    @Test
    void getAppointmentRejectsMissingAppointment() {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.getAppointment(appointmentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment not found");
    }

    @Test
    void doctorScopedGetAppointmentCannotReadAnotherDoctorsAppointment() {
        UUID appointmentId = UUID.randomUUID();
        ClinicalAccessService clinicalAccessService = org.mockito.Mockito.mock(ClinicalAccessService.class);
        AppointmentService scopedService = new AppointmentService(
                appointmentRepository,
                patientService,
                doctorService,
                communicationService,
                clinicalAccessService,
                FIXED_CLOCK
        );
        when(clinicalAccessService.scopedDoctorId()).thenReturn(Optional.of(doctorId));
        when(appointmentRepository.findByIdAndDoctorId(appointmentId, doctorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scopedService.getAppointment(appointmentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment not found");
    }

    @Test
    void getAppointmentsReturnsFilteredPage() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 2, 0, 0);
        PageRequest pageable = PageRequest.of(0, 20);
        when(appointmentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Appointment>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(java.util.List.of(appointment(AppointmentStatus.SCHEDULED))));

        assertThat(appointmentService.getAppointments(
                patientId,
                doctorId,
                AppointmentStatus.SCHEDULED,
                from,
                to,
                pageable
        ).getContent()).hasSize(1);
    }

    @Test
    void getAppointmentsRejectsInvalidRange() {
        assertThatThrownBy(() -> appointmentService.getAppointments(
                null,
                null,
                null,
                LocalDateTime.of(2026, 9, 2, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0),
                PageRequest.of(0, 20)
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Appointment date range is invalid");
    }

    @Test
    void updateAppointmentRevalidatesAvailabilityAndReturnsUpdatedAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointment(AppointmentStatus.SCHEDULED);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenReturn(doctor);

        AppointmentResponse response = appointmentService.updateAppointment(
                appointmentId,
                new AppointmentRequest(
                        patientId,
                        doctorId,
                        LocalDateTime.of(2026, 9, 1, 10, 30),
                        "Follow-up",
                        AppointmentStatus.CONFIRMED,
                        "Updated notes"
                )
        );

        assertThat(response.appointmentDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 30));
        assertThat(response.reason()).isEqualTo("Follow-up");
        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepository).existsOverlappingDoctorAppointment(eq(doctorId), any(), any(), any(), eq(appointmentId));
        verify(appointmentRepository).existsOverlappingPatientAppointment(eq(patientId), any(), any(), any(), eq(appointmentId));
    }

    @Test
    void updateAppointmentRejectsInvalidTransition() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointment(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.updateAppointment(appointmentId, request(AppointmentStatus.CONFIRMED)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Appointment status transition is invalid");
    }

    @Test
    void cancelAppointmentMarksActiveAppointmentCancelled() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        appointmentService.cancelAppointment(appointmentId);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void completeAppointmentThroughUpdate() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(doctorService.findDoctorEntity(doctorId)).thenReturn(doctor);

        AppointmentResponse response = appointmentService.updateAppointment(
                appointmentId,
                request(AppointmentStatus.COMPLETED)
        );

        assertThat(response.status()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    private AppointmentRequest request(AppointmentStatus status) {
        return new AppointmentRequest(
                patientId,
                doctorId,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "Annual checkup",
                status,
                "Bring reports"
        );
    }

    private Appointment appointment(AppointmentStatus status) {
        return new Appointment(
                patient,
                doctor,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "Annual checkup",
                status,
                "Bring reports"
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
