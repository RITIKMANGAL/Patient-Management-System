package com.patientmanagement.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.communication.config.SmsProperties;
import com.patientmanagement.communication.dto.CommunicationCreateRequest;
import com.patientmanagement.communication.dto.CommunicationResponse;
import com.patientmanagement.communication.model.Communication;
import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationStatus;
import com.patientmanagement.communication.model.CommunicationType;
import com.patientmanagement.communication.provider.CommunicationDispatchRequest;
import com.patientmanagement.communication.provider.CommunicationProvider;
import com.patientmanagement.communication.provider.CommunicationProviderResult;
import com.patientmanagement.communication.repository.CommunicationRepository;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.model.PrescriptionItem;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommunicationServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-28T10:00:00Z"),
            ZoneOffset.UTC
    );
    private static final SmsProperties SMS_PROPERTIES = new SmsProperties(
            true,
            "noop",
            "PMCLINIC",
            15,
            new SmsProperties.Templates(
                    "tpl-appointment-confirmation",
                    "tpl-appointment-reminder",
                    "tpl-consultation-completed",
                    "tpl-prescription-available",
                    "tpl-feedback-request"
            )
    );
    private static final SmsProperties DISABLED_SMS_PROPERTIES = new SmsProperties(
            false,
            "noop",
            "PMCLINIC",
            15,
            new SmsProperties.Templates(null, null, null, null, null)
    );

    @Mock
    private CommunicationRepository communicationRepository;

    @Mock
    private PatientService patientService;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CommunicationProvider smsProvider;

    private UUID patientId;
    private UUID doctorId;
    private UUID appointmentId;
    private Patient patient;
    private Doctor doctor;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        appointmentId = UUID.randomUUID();
        patient = patient();
        doctor = doctor();
        appointment = appointment();
        ReflectionTestUtils.setField(patient, "id", patientId);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
    }

    @Test
    void createCommunicationPersistsPatientCommunicationAndMarksNoOpSendSuccessful() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent("provider-100"));
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createCommunication(request(null));

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.patientName()).isEqualTo("Asha Rao");
        assertThat(response.appointmentId()).isNull();
        assertThat(response.type()).isEqualTo(CommunicationType.PRESCRIPTION_AVAILABLE);
        assertThat(response.channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(response.recipient()).isEqualTo("+15555550100");
        assertThat(response.status()).isEqualTo(CommunicationStatus.SENT);
        assertThat(response.providerMessageId()).isEqualTo("provider-100");
        assertThat(response.failureReason()).isNull();
        assertThat(response.attemptCount()).isEqualTo(1);
        assertThat(response.sentAt()).isEqualTo(Instant.now(FIXED_CLOCK));
    }

    @Test
    void createCommunicationReportsFailureWhenNoProviderIsConfigured() {
        CommunicationService service = new CommunicationService(
                communicationRepository,
                patientService,
                appointmentRepository,
                List.of(),
                SMS_PROPERTIES,
                FIXED_CLOCK
        );
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createCommunication(request(null));

        assertThat(response.status()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Communication provider is not configured");
        assertThat(response.attemptCount()).isEqualTo(1);
        assertThat(response.sentAt()).isNull();
    }

    @Test
    void createCommunicationSupportsOptionalAppointmentRelationship() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createCommunication(request(appointmentId));

        assertThat(response.appointmentId()).isEqualTo(appointmentId);
        verify(communicationRepository).existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                eq(patientId),
                eq(appointmentId),
                eq(CommunicationType.PRESCRIPTION_AVAILABLE),
                eq(CommunicationChannel.SMS),
                anyCollection()
        );
    }

    @Test
    void createAppointmentConfirmationCreatesSmsCommunicationForSavedAppointment() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent("provider-appointment-100"));
        patient.setPhone(" +15555550100 ");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createAppointmentConfirmation(appointment);

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.appointmentId()).isEqualTo(appointmentId);
        assertThat(response.type()).isEqualTo(CommunicationType.APPOINTMENT_CONFIRMATION);
        assertThat(response.channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(response.recipient()).isEqualTo("+15555550100");
        assertThat(response.status()).isEqualTo(CommunicationStatus.SENT);
        assertThat(response.providerMessageId()).isEqualTo("provider-appointment-100");
        assertThat(response.attemptCount()).isEqualTo(1);

        verify(communicationRepository).existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                eq(patientId),
                eq(appointmentId),
                eq(CommunicationType.APPOINTMENT_CONFIRMATION),
                eq(CommunicationChannel.SMS),
                anyCollection()
        );

        ArgumentCaptor<CommunicationDispatchRequest> dispatchCaptor =
                ArgumentCaptor.forClass(CommunicationDispatchRequest.class);
        verify(smsProvider).send(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue().type()).isEqualTo(CommunicationType.APPOINTMENT_CONFIRMATION);
        assertThat(dispatchCaptor.getValue().channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(dispatchCaptor.getValue().recipient()).isEqualTo("+15555550100");
        assertThat(dispatchCaptor.getValue().sender()).isEqualTo("PMCLINIC");
        assertThat(dispatchCaptor.getValue().templateId()).isEqualTo("tpl-appointment-confirmation");
    }

    @Test
    void createAppointmentConfirmationRecordsFailureWhenPatientPhoneIsBlank() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        patient.setPhone(" ");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createAppointmentConfirmation(appointment);

        assertThat(response.type()).isEqualTo(CommunicationType.APPOINTMENT_CONFIRMATION);
        assertThat(response.recipient()).isEmpty();
        assertThat(response.status()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Communication recipient is required");
        verify(smsProvider, never()).send(any(CommunicationDispatchRequest.class));
    }

    @Test
    void createAppointmentReminderCreatesSmsCommunicationForSavedAppointment() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent("provider-reminder-100"));
        patient.setPhone(" +15555550100 ");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createAppointmentReminder(appointment);

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.appointmentId()).isEqualTo(appointmentId);
        assertThat(response.type()).isEqualTo(CommunicationType.APPOINTMENT_REMINDER);
        assertThat(response.channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(response.recipient()).isEqualTo("+15555550100");
        assertThat(response.status()).isEqualTo(CommunicationStatus.SENT);
        assertThat(response.providerMessageId()).isEqualTo("provider-reminder-100");

        verify(communicationRepository).existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                eq(patientId),
                eq(appointmentId),
                eq(CommunicationType.APPOINTMENT_REMINDER),
                eq(CommunicationChannel.SMS),
                anyCollection()
        );
    }

    @Test
    void createConsultationCompletedCreatesSmsCommunicationForSavedAppointment() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent("provider-consultation-100"));
        patient.setPhone(" +15555550100 ");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        service.createConsultationCompleted(appointment);

        ArgumentCaptor<Communication> communicationCaptor = ArgumentCaptor.forClass(Communication.class);
        verify(communicationRepository).save(communicationCaptor.capture());
        Communication communication = communicationCaptor.getValue();
        assertThat(communication.getPatient().getId()).isEqualTo(patientId);
        assertThat(communication.getAppointment().getId()).isEqualTo(appointmentId);
        assertThat(communication.getType()).isEqualTo(CommunicationType.CONSULTATION_COMPLETED);
        assertThat(communication.getChannel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(communication.getRecipient()).isEqualTo("+15555550100");
        assertThat(communication.getStatus()).isEqualTo(CommunicationStatus.SENT);
        assertThat(communication.getProviderMessageId()).isEqualTo("provider-consultation-100");
        assertThat(communication.getAttemptCount()).isEqualTo(1);

        verify(communicationRepository).existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                eq(patientId),
                eq(appointmentId),
                eq(CommunicationType.CONSULTATION_COMPLETED),
                eq(CommunicationChannel.SMS),
                anyCollection()
        );
    }

    @Test
    void createConsultationCompletedDispatchesGenericPrivacySafeMessage() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        appointment.setNotes("Sensitive AI output, diagnosis, symptoms, medications, and clinical notes");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        service.createConsultationCompleted(appointment);

        ArgumentCaptor<CommunicationDispatchRequest> dispatchCaptor =
                ArgumentCaptor.forClass(CommunicationDispatchRequest.class);
        verify(smsProvider).send(dispatchCaptor.capture());
        CommunicationDispatchRequest dispatchRequest = dispatchCaptor.getValue();
        assertThat(dispatchRequest.type()).isEqualTo(CommunicationType.CONSULTATION_COMPLETED);
        assertThat(dispatchRequest.channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(dispatchRequest.recipient()).isEqualTo("+15555550100");
        assertThat(dispatchRequest.message())
                .isEqualTo("Your consultation has been completed. Please contact the clinic if you have any questions.")
                .doesNotContain("diagnosis")
                .doesNotContain("symptoms")
                .doesNotContain("medications")
                .doesNotContain("AI")
                .doesNotContain("clinical notes");
        assertThat(dispatchRequest.toString())
                .doesNotContain("Sensitive AI output")
                .doesNotContain("diagnosis")
                .doesNotContain("medications");
    }

    @Test
    void createConsultationCompletedRecordsFailedCommunicationWhenPatientPhoneIsBlank() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        patient.setPhone(" ");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        service.createConsultationCompleted(appointment);

        ArgumentCaptor<Communication> communicationCaptor = ArgumentCaptor.forClass(Communication.class);
        verify(communicationRepository).save(communicationCaptor.capture());
        Communication communication = communicationCaptor.getValue();
        assertThat(communication.getType()).isEqualTo(CommunicationType.CONSULTATION_COMPLETED);
        assertThat(communication.getChannel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(communication.getAppointment().getId()).isEqualTo(appointmentId);
        assertThat(communication.getRecipient()).isEmpty();
        assertThat(communication.getStatus()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(communication.getFailureReason()).isEqualTo("Communication recipient is required");
        assertThat(communication.getAttemptCount()).isEqualTo(1);
        verify(smsProvider, never()).send(any(CommunicationDispatchRequest.class));
    }

    @Test
    void createConsultationCompletedRecordsProviderFailure() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.failed("Provider rejected recipient"));
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        service.createConsultationCompleted(appointment);

        ArgumentCaptor<Communication> communicationCaptor = ArgumentCaptor.forClass(Communication.class);
        verify(communicationRepository).save(communicationCaptor.capture());
        Communication communication = communicationCaptor.getValue();
        assertThat(communication.getType()).isEqualTo(CommunicationType.CONSULTATION_COMPLETED);
        assertThat(communication.getStatus()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(communication.getFailureReason()).isEqualTo("Provider rejected recipient");
        assertThat(communication.getAttemptCount()).isEqualTo(1);
        assertThat(communication.getSentAt()).isNull();
    }

    @Test
    void createFeedbackRequestCreatesSmsCommunicationWithFeedbackLinkOnlyInDispatchMessage() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent("provider-feedback-100"));
        patient.setPhone(" +15555550100 ");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        service.createFeedbackRequest(appointment, "http://localhost:5173/feedback/raw-token");

        ArgumentCaptor<Communication> communicationCaptor = ArgumentCaptor.forClass(Communication.class);
        verify(communicationRepository).save(communicationCaptor.capture());
        Communication communication = communicationCaptor.getValue();
        assertThat(communication.getType()).isEqualTo(CommunicationType.FEEDBACK_REQUEST);
        assertThat(communication.getChannel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(communication.getRecipient()).isEqualTo("+15555550100");
        assertThat(communication.getStatus()).isEqualTo(CommunicationStatus.SENT);
        assertThat(communication.getProviderMessageId()).isEqualTo("provider-feedback-100");

        ArgumentCaptor<CommunicationDispatchRequest> dispatchCaptor =
                ArgumentCaptor.forClass(CommunicationDispatchRequest.class);
        verify(smsProvider).send(dispatchCaptor.capture());
        CommunicationDispatchRequest dispatchRequest = dispatchCaptor.getValue();
        assertThat(dispatchRequest.type()).isEqualTo(CommunicationType.FEEDBACK_REQUEST);
        assertThat(dispatchRequest.sender()).isEqualTo("PMCLINIC");
        assertThat(dispatchRequest.templateId()).isEqualTo("tpl-feedback-request");
        assertThat(dispatchRequest.message())
                .isEqualTo("Please share feedback about your recent clinic visit: http://localhost:5173/feedback/raw-token")
                .doesNotContain("diagnosis")
                .doesNotContain("symptoms")
                .doesNotContain("clinical notes");
        assertThat(dispatchRequest.toString())
                .doesNotContain("raw-token")
                .doesNotContain("+15555550100");
    }

    @Test
    void createFeedbackRequestRecordsFailedCommunicationWhenPatientPhoneIsBlank() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        patient.setPhone(" ");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        service.createFeedbackRequest(appointment, "http://localhost:5173/feedback/raw-token");

        ArgumentCaptor<Communication> communicationCaptor = ArgumentCaptor.forClass(Communication.class);
        verify(communicationRepository).save(communicationCaptor.capture());
        Communication communication = communicationCaptor.getValue();
        assertThat(communication.getType()).isEqualTo(CommunicationType.FEEDBACK_REQUEST);
        assertThat(communication.getStatus()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(communication.getFailureReason()).isEqualTo("Communication recipient is required");
        verify(smsProvider, never()).send(any(CommunicationDispatchRequest.class));
    }

    @Test
    void createFeedbackRequestSkipsActiveDuplicateForSameAppointment() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        when(communicationRepository.existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                eq(patientId),
                eq(appointmentId),
                eq(CommunicationType.FEEDBACK_REQUEST),
                eq(CommunicationChannel.SMS),
                anyCollection()
        )).thenReturn(true);

        service.createFeedbackRequest(appointment, "http://localhost:5173/feedback/raw-token");

        verify(communicationRepository, never()).save(any(Communication.class));
        verify(smsProvider, never()).send(any(CommunicationDispatchRequest.class));
    }

    @Test
    void createConsultationCompletedSkipsActiveDuplicateForSameAppointment() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        when(communicationRepository.existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                eq(patientId),
                eq(appointmentId),
                eq(CommunicationType.CONSULTATION_COMPLETED),
                eq(CommunicationChannel.SMS),
                anyCollection()
        )).thenReturn(true);

        service.createConsultationCompleted(appointment);

        verify(communicationRepository, never()).save(any(Communication.class));
        verify(smsProvider, never()).send(any(CommunicationDispatchRequest.class));
    }

    @Test
    void createPrescriptionAvailableCreatesSmsCommunicationForSavedPrescription() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent("provider-prescription-100"));
        patient.setPhone(" +15555550100 ");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createPrescriptionAvailable(prescription());

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.appointmentId()).isNull();
        assertThat(response.type()).isEqualTo(CommunicationType.PRESCRIPTION_AVAILABLE);
        assertThat(response.channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(response.recipient()).isEqualTo("+15555550100");
        assertThat(response.status()).isEqualTo(CommunicationStatus.SENT);
        assertThat(response.providerMessageId()).isEqualTo("provider-prescription-100");
        assertThat(response.attemptCount()).isEqualTo(1);

        verify(communicationRepository, never()).existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                any(),
                any(),
                any(),
                any(),
                anyCollection()
        );
    }

    @Test
    void createPrescriptionAvailableDispatchesOnlyDeliveryAuditFields() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        service.createPrescriptionAvailable(sensitivePrescription());

        ArgumentCaptor<CommunicationDispatchRequest> dispatchCaptor =
                ArgumentCaptor.forClass(CommunicationDispatchRequest.class);
        verify(smsProvider).send(dispatchCaptor.capture());
        CommunicationDispatchRequest dispatchRequest = dispatchCaptor.getValue();
        assertThat(dispatchRequest.type()).isEqualTo(CommunicationType.PRESCRIPTION_AVAILABLE);
        assertThat(dispatchRequest.channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(dispatchRequest.recipient()).isEqualTo("+15555550100");
        assertThat(dispatchRequest.message()).isEqualTo(
                "Your prescription is available. Please contact the clinic if you have any questions."
        );
        assertThat(dispatchRequest.toString())
                .doesNotContain("Sensitive medicine")
                .doesNotContain("10mg")
                .doesNotContain("Sensitive prescription notes");
    }

    @Test
    void createPrescriptionAvailableRecordsProviderFailure() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.failed("Provider rejected recipient"));
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createPrescriptionAvailable(prescription());

        assertThat(response.type()).isEqualTo(CommunicationType.PRESCRIPTION_AVAILABLE);
        assertThat(response.status()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Provider rejected recipient");
        assertThat(response.attemptCount()).isEqualTo(1);
        assertThat(response.sentAt()).isNull();
    }

    @Test
    void createPrescriptionAvailableRecordsFailedCommunicationWhenPatientPhoneIsBlank() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        patient.setPhone(" ");
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createPrescriptionAvailable(prescription());

        assertThat(response.type()).isEqualTo(CommunicationType.PRESCRIPTION_AVAILABLE);
        assertThat(response.channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(response.recipient()).isEmpty();
        assertThat(response.status()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Communication recipient is required");
        assertThat(response.attemptCount()).isEqualTo(1);
        verify(smsProvider, never()).send(any(CommunicationDispatchRequest.class));
    }

    @Test
    void createDueAppointmentRemindersCreatesReminderForEligibleAppointmentsAndSkipsDuplicates() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 2, 0, 0);
        UUID duplicateAppointmentId = UUID.randomUUID();
        Appointment duplicateAppointment = appointment();
        ReflectionTestUtils.setField(duplicateAppointment, "id", duplicateAppointmentId);
        when(appointmentRepository.findByAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusIn(
                eq(from),
                eq(to),
                eq(EnumSet.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED))
        )).thenReturn(List.of(appointment, duplicateAppointment));
        when(communicationRepository.existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                eq(patientId),
                eq(appointmentId),
                eq(CommunicationType.APPOINTMENT_REMINDER),
                eq(CommunicationChannel.SMS),
                anyCollection()
        )).thenReturn(false);
        when(communicationRepository.existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                eq(patientId),
                eq(duplicateAppointmentId),
                eq(CommunicationType.APPOINTMENT_REMINDER),
                eq(CommunicationChannel.SMS),
                anyCollection()
        )).thenReturn(true);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        List<CommunicationResponse> responses = service.createDueAppointmentReminders(from, to);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().appointmentId()).isEqualTo(appointmentId);
        assertThat(responses.getFirst().type()).isEqualTo(CommunicationType.APPOINTMENT_REMINDER);
        verify(smsProvider).send(any(CommunicationDispatchRequest.class));
    }

    @Test
    void createDueAppointmentRemindersRejectsInvalidWindow() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));

        assertThatThrownBy(() -> service.createDueAppointmentReminders(
                LocalDateTime.of(2026, 9, 2, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0)
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Reminder date range is invalid");
    }

    @Test
    void createCommunicationRejectsMissingType() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));

        CommunicationCreateRequest request = new CommunicationCreateRequest(
                patientId,
                null,
                null,
                CommunicationChannel.SMS,
                "+15555550100"
        );

        assertThatThrownBy(() -> service.createCommunication(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Communication type is required");
    }

    @Test
    void createCommunicationRejectsMissingChannel() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));

        CommunicationCreateRequest request = new CommunicationCreateRequest(
                patientId,
                null,
                CommunicationType.PRESCRIPTION_AVAILABLE,
                null,
                "+15555550100"
        );

        assertThatThrownBy(() -> service.createCommunication(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Communication channel is required");
    }

    @Test
    void createCommunicationRejectsMissingPatient() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        when(patientService.findPatientEntity(patientId)).thenThrow(new ResourceNotFoundException("Patient not found"));

        assertThatThrownBy(() -> service.createCommunication(request(null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    @Test
    void createCommunicationRejectsMissingAppointment() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCommunication(request(appointmentId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment not found");
    }

    @Test
    void createCommunicationRejectsAppointmentForDifferentPatient() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        Patient otherPatient = patient();
        ReflectionTestUtils.setField(otherPatient, "id", UUID.randomUUID());
        Appointment otherAppointment = new Appointment(
                otherPatient,
                doctor,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "Annual checkup",
                AppointmentStatus.SCHEDULED,
                null
        );
        ReflectionTestUtils.setField(otherAppointment, "id", appointmentId);
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(otherAppointment));

        assertThatThrownBy(() -> service.createCommunication(request(appointmentId)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Appointment does not belong to patient");
    }

    @Test
    void createCommunicationRejectsActiveDuplicateForSameAppointment() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(communicationRepository.existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                eq(patientId),
                eq(appointmentId),
                eq(CommunicationType.PRESCRIPTION_AVAILABLE),
                eq(CommunicationChannel.SMS),
                anyCollection()
        )).thenReturn(true);

        assertThatThrownBy(() -> service.createCommunication(request(appointmentId)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Communication already exists for this patient and appointment");
    }

    @Test
    void createCommunicationRecordsProviderFailure() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.failed("Provider rejected recipient"));
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createCommunication(request(null));

        assertThat(response.status()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Provider rejected recipient");
        assertThat(response.attemptCount()).isEqualTo(1);
        assertThat(response.sentAt()).isNull();
    }

    @Test
    void createCommunicationRecordsProviderExceptionWithoutExposingProviderDetails() {
        when(smsProvider.channel()).thenReturn(CommunicationChannel.SMS);
        when(smsProvider.send(any(CommunicationDispatchRequest.class))).thenThrow(new IllegalStateException("secret provider detail"));
        CommunicationService service = new CommunicationService(
                communicationRepository,
                patientService,
                appointmentRepository,
                List.of(smsProvider),
                SMS_PROPERTIES,
                FIXED_CLOCK
        );
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createCommunication(request(null));

        assertThat(response.status()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Communication provider failed");
        assertThat(response.attemptCount()).isEqualTo(1);
    }

    @Test
    void disabledSmsPersistsCommunicationWithoutCallingProvider() {
        when(smsProvider.channel()).thenReturn(CommunicationChannel.SMS);
        CommunicationService service = new CommunicationService(
                communicationRepository,
                patientService,
                appointmentRepository,
                List.of(smsProvider),
                DISABLED_SMS_PROPERTIES,
                FIXED_CLOCK
        );
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        CommunicationResponse response = service.createCommunication(request(null));

        assertThat(response.status()).isEqualTo(CommunicationStatus.DISABLED);
        assertThat(response.attemptCount()).isZero();
        assertThat(response.sentAt()).isNull();
        verify(smsProvider, never()).send(any(CommunicationDispatchRequest.class));
    }

    @Test
    void getCommunicationReturnsExistingCommunication() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        UUID communicationId = UUID.randomUUID();
        Communication communication = new Communication(
                patient,
                null,
                CommunicationType.FEEDBACK_REQUEST,
                CommunicationChannel.SMS,
                "+15555550100"
        );
        ReflectionTestUtils.setField(communication, "id", communicationId);
        when(communicationRepository.findById(communicationId)).thenReturn(Optional.of(communication));

        CommunicationResponse response = service.getCommunication(communicationId);

        assertThat(response.id()).isEqualTo(communicationId);
        assertThat(response.type()).isEqualTo(CommunicationType.FEEDBACK_REQUEST);
    }

    @Test
    void getCommunicationRejectsMissingCommunication() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        UUID communicationId = UUID.randomUUID();
        when(communicationRepository.findById(communicationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCommunication(communicationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Communication not found");
    }

    @Test
    void getCommunicationsReturnsPagedHistory() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent(null));
        Communication communication = new Communication(
                patient,
                null,
                CommunicationType.APPOINTMENT_REMINDER,
                CommunicationChannel.SMS,
                "+15555550100"
        );
        ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
        PageRequest pageable = PageRequest.of(0, 20);
        when(communicationRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(communication), pageable, 1));

        assertThat(service.getCommunications(pageable).getContent())
                .extracting(CommunicationResponse::type)
                .containsExactly(CommunicationType.APPOINTMENT_REMINDER);
    }

    @Test
    void providerReceivesOnlyDeliveryAuditFields() {
        CommunicationService service = serviceWithProvider(CommunicationProviderResult.sent("provider-100"));
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(invocation -> {
            Communication communication = invocation.getArgument(0);
            ReflectionTestUtils.setField(communication, "id", UUID.randomUUID());
            return communication;
        });

        service.createCommunication(request(null));

        ArgumentCaptor<CommunicationDispatchRequest> captor = ArgumentCaptor.forClass(CommunicationDispatchRequest.class);
        verify(smsProvider).send(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(CommunicationType.PRESCRIPTION_AVAILABLE);
        assertThat(captor.getValue().channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(captor.getValue().recipient()).isEqualTo("+15555550100");
        assertThat(captor.getValue().message()).isEqualTo(
                "Your prescription is available. Please contact the clinic if you have any questions."
        );
    }

    private CommunicationService serviceWithProvider(CommunicationProviderResult result) {
        when(smsProvider.channel()).thenReturn(CommunicationChannel.SMS);
        lenient().when(smsProvider.send(any(CommunicationDispatchRequest.class))).thenReturn(result);
        return new CommunicationService(
                communicationRepository,
                patientService,
                appointmentRepository,
                List.of(smsProvider),
                SMS_PROPERTIES,
                FIXED_CLOCK
        );
    }

    private CommunicationCreateRequest request(UUID requestAppointmentId) {
        return new CommunicationCreateRequest(
                patientId,
                requestAppointmentId,
                CommunicationType.PRESCRIPTION_AVAILABLE,
                CommunicationChannel.SMS,
                " +15555550100 "
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

    private Appointment appointment() {
        return new Appointment(
                patient,
                doctor,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "Annual checkup",
                AppointmentStatus.SCHEDULED,
                null
        );
    }

    private Prescription prescription() {
        Prescription prescription = new Prescription(
                patient,
                doctor,
                LocalDate.of(2026, 8, 28),
                "Prescription is ready"
        );
        prescription.addItem(new PrescriptionItem("Synthetic medicine", "10mg", "Once daily", "5 days", "After food"));
        ReflectionTestUtils.setField(prescription, "id", UUID.randomUUID());
        return prescription;
    }

    private Prescription sensitivePrescription() {
        Prescription prescription = new Prescription(
                patient,
                doctor,
                LocalDate.of(2026, 8, 28),
                "Sensitive prescription notes"
        );
        prescription.addItem(new PrescriptionItem("Sensitive medicine", "10mg", "Twice daily", "7 days", "After food"));
        ReflectionTestUtils.setField(prescription, "id", UUID.randomUUID());
        return prescription;
    }
}
