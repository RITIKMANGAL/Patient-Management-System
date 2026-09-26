package com.patientmanagement.consultation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.consultation.dto.ConsultationResponse;
import com.patientmanagement.consultation.dto.ConsultationUpdateRequest;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.consultation.model.ConsultationStatus;
import com.patientmanagement.consultation.repository.ConsultationRepository;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.feedback.service.FeedbackService;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.service.PatientService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientService patientService;

    @Mock
    private CommunicationService communicationService;

    @Mock
    private FeedbackService feedbackService;

    private ConsultationService consultationService;
    private UUID patientId;
    private UUID doctorId;
    private UUID appointmentId;
    private UUID consultationId;
    private Patient patient;
    private Doctor doctor;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        consultationService = new ConsultationService(
                consultationRepository,
                appointmentRepository,
                patientService,
                communicationService,
                feedbackService,
                FIXED_CLOCK
        );
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        appointmentId = UUID.randomUUID();
        consultationId = UUID.randomUUID();
        patient = patient();
        doctor = doctor();
        appointment = appointment(AppointmentStatus.CONFIRMED);
        ReflectionTestUtils.setField(patient, "id", patientId);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
    }

    @Test
    void startConsultationCreatesInProgressConsultationForEligibleAppointment() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(consultationRepository.existsByAppointmentId(appointmentId)).thenReturn(false);
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(invocation -> {
            Consultation consultation = invocation.getArgument(0);
            ReflectionTestUtils.setField(consultation, "id", consultationId);
            return consultation;
        });

        ConsultationResponse response = consultationService.startConsultation(appointmentId);

        assertThat(response.id()).isEqualTo(consultationId);
        assertThat(response.appointmentId()).isEqualTo(appointmentId);
        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.patientName()).isEqualTo("Asha Rao");
        assertThat(response.doctorId()).isEqualTo(doctorId);
        assertThat(response.doctorName()).isEqualTo("Kiran Shah");
        assertThat(response.status()).isEqualTo(ConsultationStatus.IN_PROGRESS);
        assertThat(response.startedAt()).isEqualTo(Instant.now(FIXED_CLOCK));
        assertThat(response.completedAt()).isNull();

        ArgumentCaptor<Consultation> consultationCaptor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(consultationCaptor.capture());
        assertThat(consultationCaptor.getValue().getAppointment()).isSameAs(appointment);
    }

    @Test
    void startConsultationRejectsMissingAppointment() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationService.startConsultation(appointmentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment not found");
        verifyNoInteractions(consultationRepository);
    }

    @Test
    void startConsultationRejectsCompletedAppointment() {
        appointment.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> consultationService.startConsultation(appointmentId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Consultation can only be started for scheduled or confirmed appointments");
    }

    @Test
    void startConsultationRejectsDuplicateAppointmentConsultation() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(consultationRepository.existsByAppointmentId(appointmentId)).thenReturn(true);

        assertThatThrownBy(() -> consultationService.startConsultation(appointmentId))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Consultation already exists for appointment");
    }

    @Test
    void startConsultationMapsDatabaseDuplicateToConflict() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(consultationRepository.existsByAppointmentId(appointmentId)).thenReturn(false);
        when(consultationRepository.save(any(Consultation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate appointment"));

        assertThatThrownBy(() -> consultationService.startConsultation(appointmentId))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Consultation already exists for appointment");
    }

    @Test
    void updateConsultationStoresTrimmedClinicalFieldsWhileInProgress() {
        Consultation consultation = consultation();
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        ConsultationResponse response = consultationService.updateConsultation(
                consultationId,
                new ConsultationUpdateRequest(
                        " Chest pain ",
                        " Fatigue ",
                        " Stable vitals ",
                        " Musculoskeletal pain ",
                        " Rest and hydration ",
                        " Review in one week "
                )
        );

        assertThat(response.chiefComplaint()).isEqualTo("Chest pain");
        assertThat(response.symptoms()).isEqualTo("Fatigue");
        assertThat(response.examination()).isEqualTo("Stable vitals");
        assertThat(response.assessment()).isEqualTo("Musculoskeletal pain");
        assertThat(response.treatment()).isEqualTo("Rest and hydration");
        assertThat(response.followUpInstructions()).isEqualTo("Review in one week");
    }

    @Test
    void updateConsultationRejectsCompletedConsultation() {
        Consultation consultation = consultation();
        consultation.updateClinicalNotes("Complaint", null, null, "Assessment", null, null);
        consultation.complete(Instant.now(FIXED_CLOCK));
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> consultationService.updateConsultation(consultationId, updateRequest()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Only in-progress consultations can be modified");
    }

    @Test
    void completeConsultationMarksConsultationAndAppointmentCompleted() {
        Consultation consultation = consultation();
        consultation.updateClinicalNotes("Chest pain", null, null, "Musculoskeletal pain", "Rest", null);
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        ConsultationResponse response = consultationService.completeConsultation(consultationId);

        assertThat(response.status()).isEqualTo(ConsultationStatus.COMPLETED);
        assertThat(response.completedAt()).isEqualTo(Instant.now(FIXED_CLOCK));
        assertThat(response.appointmentStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        verify(communicationService).createConsultationCompleted(appointment);
        verify(feedbackService).createFeedbackRequestForCompletedConsultation(consultation);
    }

    @Test
    void completeConsultationStillCompletesWhenFeedbackRequestFails() {
        Consultation consultation = consultation();
        consultation.updateClinicalNotes("Chest pain", null, null, "Musculoskeletal pain", "Rest", null);
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        org.mockito.Mockito.doThrow(new IllegalStateException("provider detail"))
                .when(feedbackService).createFeedbackRequestForCompletedConsultation(consultation);

        ConsultationResponse response = consultationService.completeConsultation(consultationId);

        assertThat(response.status()).isEqualTo(ConsultationStatus.COMPLETED);
        assertThat(response.appointmentStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        verify(communicationService).createConsultationCompleted(appointment);
        verify(feedbackService).createFeedbackRequestForCompletedConsultation(consultation);
    }

    @Test
    void completeConsultationStillCompletesWhenCompletedCommunicationFails() {
        Consultation consultation = consultation();
        consultation.updateClinicalNotes("Chest pain", null, null, "Musculoskeletal pain", "Rest", null);
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        doThrow(new IllegalStateException("provider unavailable"))
                .when(communicationService).createConsultationCompleted(appointment);

        ConsultationResponse response = consultationService.completeConsultation(consultationId);

        assertThat(response.status()).isEqualTo(ConsultationStatus.COMPLETED);
        assertThat(response.appointmentStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        verify(communicationService).createConsultationCompleted(appointment);
        verify(feedbackService).createFeedbackRequestForCompletedConsultation(consultation);
    }

    @Test
    void completeConsultationIsIdempotentWhenAlreadyCompleted() {
        Consultation consultation = consultation();
        consultation.updateClinicalNotes("Chest pain", null, null, "Assessment", null, null);
        Instant completedAt = Instant.parse("2026-09-03T09:00:00Z");
        consultation.complete(completedAt);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        ConsultationResponse response = consultationService.completeConsultation(consultationId);

        assertThat(response.status()).isEqualTo(ConsultationStatus.COMPLETED);
        assertThat(response.completedAt()).isEqualTo(completedAt);
        verifyNoInteractions(communicationService);
        verifyNoInteractions(feedbackService);
    }

    @Test
    void completeConsultationRequiresChiefComplaintAndAssessment() {
        Consultation consultation = consultation();
        consultation.updateClinicalNotes(null, "Fatigue", null, null, null, null);
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> consultationService.completeConsultation(consultationId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Chief complaint is required before completion");
        verifyNoInteractions(communicationService);
        verifyNoInteractions(feedbackService);
    }

    @Test
    void completeConsultationRejectsCancelledConsultation() {
        Consultation consultation = consultation();
        ReflectionTestUtils.setField(consultation, "status", ConsultationStatus.CANCELLED);
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> consultationService.completeConsultation(consultationId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Only in-progress consultations can be modified");
        verifyNoInteractions(communicationService);
        verifyNoInteractions(feedbackService);
    }

    @Test
    void completeConsultationRejectsMissingConsultationWithoutCreatingCommunication() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationService.completeConsultation(consultationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Consultation not found");
        verifyNoInteractions(communicationService);
        verifyNoInteractions(feedbackService);
    }

    @Test
    void doctorScopedUpdateConsultationCannotModifyAnotherDoctorsConsultation() {
        ClinicalAccessService clinicalAccessService = org.mockito.Mockito.mock(ClinicalAccessService.class);
        ConsultationService scopedService = new ConsultationService(
                consultationRepository,
                appointmentRepository,
                patientService,
                communicationService,
                feedbackService,
                clinicalAccessService,
                FIXED_CLOCK
        );
        when(clinicalAccessService.scopedDoctorId()).thenReturn(Optional.of(doctorId));
        when(consultationRepository.findByIdAndAppointmentDoctorId(consultationId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> scopedService.updateConsultation(consultationId, updateRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Consultation not found");
    }

    @Test
    void getAppointmentConsultationReturnsExistingConsultation() {
        Consultation consultation = consultation();
        when(consultationRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.of(consultation));

        assertThat(consultationService.getAppointmentConsultation(appointmentId).appointmentId())
                .isEqualTo(appointmentId);
    }

    @Test
    void getPatientConsultationsValidatesPatientAndReturnsPage() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(consultationRepository.findByAppointmentPatientId(patientId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(consultation())));

        assertThat(consultationService.getPatientConsultations(patientId, PageRequest.of(0, 20)).getContent())
                .hasSize(1);
    }

    private ConsultationUpdateRequest updateRequest() {
        return new ConsultationUpdateRequest(
                "Chest pain",
                "Fatigue",
                "Stable vitals",
                "Musculoskeletal pain",
                "Rest",
                "Review in one week"
        );
    }

    private Consultation consultation() {
        Consultation consultation = new Consultation(appointment, Instant.now(FIXED_CLOCK));
        ReflectionTestUtils.setField(consultation, "id", consultationId);
        return consultation;
    }

    private Appointment appointment(AppointmentStatus status) {
        return new Appointment(
                patient,
                doctor,
                LocalDateTime.of(2026, 9, 3, 10, 0),
                "Follow-up",
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
