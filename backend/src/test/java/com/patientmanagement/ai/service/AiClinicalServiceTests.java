package com.patientmanagement.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.ai.dto.AiConsultationDraft;
import com.patientmanagement.ai.dto.AiConsultationDraftRequest;
import com.patientmanagement.ai.dto.AiConsultationDraftResponse;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;
import com.patientmanagement.ai.provider.AiConsultationDraftInput;
import com.patientmanagement.ai.provider.AiPatientHistoryInput;
import com.patientmanagement.ai.provider.AiProvider;
import com.patientmanagement.ai.provider.AiProviderException;
import com.patientmanagement.ai.provider.AiProviderMalformedResponseException;
import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.consultation.repository.ConsultationRepository;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.medicalrecord.model.MedicalRecord;
import com.patientmanagement.medicalrecord.repository.MedicalRecordRepository;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.model.PrescriptionItem;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiClinicalServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private AiProvider aiProvider;

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private PatientService patientService;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    private AiClinicalService aiClinicalService;
    private UUID consultationId;
    private UUID patientId;
    private Patient patient;
    private Doctor doctor;
    private Appointment appointment;
    private Consultation consultation;

    @BeforeEach
    void setUp() {
        aiClinicalService = service(new AiProperties(true, "mock", "test-model", 5, "", "https://example.test/chat"));
        consultationId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        patient = patient();
        doctor = doctor();
        appointment = new Appointment(
                patient,
                doctor,
                LocalDateTime.of(2026, 9, 3, 10, 0),
                "Follow-up",
                AppointmentStatus.CONFIRMED,
                "Bring reports"
        );
        consultation = new Consultation(appointment, Instant.parse("2026-09-03T10:00:00Z"));
        ReflectionTestUtils.setField(patient, "id", patientId);
        ReflectionTestUtils.setField(doctor, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(appointment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(consultation, "id", consultationId);
    }

    @Test
    void generateConsultationDraftReturnsDraftWithoutPersistingConsultation() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiProvider.generateConsultationDraft(any())).thenReturn(new AiConsultationDraft(
                "Fever",
                "Cough",
                "Temperature 100.4 F",
                "Not provided",
                "Rest and fluids",
                "Not provided"
        ));
        when(aiProvider.providerName()).thenReturn("mock");

        AiConsultationDraftResponse response = aiClinicalService.generateConsultationDraft(
                consultationId,
                draftRequest()
        );

        assertThat(response.draft().chiefComplaint()).isEqualTo("Fever");
        assertThat(response.notice()).isEqualTo("AI-generated draft - review before saving.");
        assertThat(response.generatedAt()).isEqualTo(Instant.now(FIXED_CLOCK));
        verify(consultationRepository, never()).save(any());
    }

    @Test
    void generateConsultationDraftPassesOnlyPurposeSpecificInput() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiProvider.generateConsultationDraft(any())).thenReturn(new AiConsultationDraft(
                "Fever",
                "Cough",
                "Not provided",
                "Not provided",
                "Rest",
                "Not provided"
        ));
        when(aiProvider.providerName()).thenReturn("mock");

        aiClinicalService.generateConsultationDraft(consultationId, draftRequest());

        ArgumentCaptor<AiConsultationDraftInput> inputCaptor = ArgumentCaptor.forClass(AiConsultationDraftInput.class);
        verify(aiProvider).generateConsultationDraft(inputCaptor.capture());
        assertThat(inputCaptor.getValue().roughNotes()).contains("fever");
        assertThat(inputCaptor.getValue().roughNotes()).doesNotContain(consultationId.toString());
    }

    @Test
    void generateConsultationDraftRejectsMissingConsultation() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiClinicalService.generateConsultationDraft(consultationId, draftRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Consultation not found");
        verify(aiProvider, never()).generateConsultationDraft(any());
    }

    @Test
    void generateConsultationDraftRejectsAnotherDoctorsConsultationBeforeCallingProvider() {
        ClinicalAccessService clinicalAccessService = org.mockito.Mockito.mock(ClinicalAccessService.class);
        aiClinicalService = new AiClinicalService(
                aiProvider,
                new AiProperties(true, "mock", "test-model", 5, "", "https://example.test/chat"),
                consultationRepository,
                patientService,
                appointmentRepository,
                medicalRecordRepository,
                prescriptionRepository,
                clinicalAccessService,
                FIXED_CLOCK
        );
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        doThrow(new ResourceNotFoundException("Consultation not found"))
                .when(clinicalAccessService).requireConsultationAccess(consultation);

        assertThatThrownBy(() -> aiClinicalService.generateConsultationDraft(consultationId, draftRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Consultation not found");
        verify(aiProvider, never()).generateConsultationDraft(any());
    }

    @Test
    void generateConsultationDraftMapsProviderFailureToSafeUnavailableError() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiProvider.generateConsultationDraft(any())).thenThrow(new AiProviderException("provider leaked detail"));

        assertThatThrownBy(() -> aiClinicalService.generateConsultationDraft(consultationId, draftRequest()))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("AI assistance is temporarily unavailable. You can continue entering the consultation manually.");
    }

    @Test
    void generateConsultationDraftRejectsMalformedProviderResponse() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiProvider.generateConsultationDraft(any())).thenReturn(new AiConsultationDraft(
                "Fever",
                "Cough",
                "Not provided",
                null,
                "Rest",
                "Not provided"
        ));

        assertThatThrownBy(() -> aiClinicalService.generateConsultationDraft(consultationId, draftRequest()))
                .isInstanceOf(AiProviderResponseException.class)
                .hasMessage("AI response could not be processed. You can continue entering the consultation manually.");
    }

    @Test
    void generateConsultationDraftMapsMalformedProviderPayloadToSafeBadGatewayError() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiProvider.generateConsultationDraft(any()))
                .thenThrow(new AiProviderMalformedResponseException("raw provider response was not JSON"));

        assertThatThrownBy(() -> aiClinicalService.generateConsultationDraft(consultationId, draftRequest()))
                .isInstanceOf(AiProviderResponseException.class)
                .hasMessage("AI response could not be processed. You can continue entering the consultation manually.");
    }

    @Test
    void generateConsultationDraftRejectsDisabledAi() {
        aiClinicalService = service(new AiProperties(false, "mock", "test-model", 5, "", "https://example.test/chat"));
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> aiClinicalService.generateConsultationDraft(consultationId, draftRequest()))
                .isInstanceOf(AiServiceUnavailableException.class);
        verify(aiProvider, never()).generateConsultationDraft(any());
    }

    @Test
    void summarizePatientHistoryBuildsSummaryFromAvailablePatientData() {
        consultation.updateClinicalNotes("Fever", "Cough", "Temperature 100.4 F", "Not provided", "Rest", "Review in one week");
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(appointmentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(appointment)));
        when(consultationRepository.findByAppointmentPatientId(any(), any()))
                .thenReturn(new PageImpl<>(List.of(consultation)));
        when(medicalRecordRepository.findByPatientId(any(), any()))
                .thenReturn(new PageImpl<>(List.of(medicalRecord())));
        when(prescriptionRepository.findByPatientId(any(), any()))
                .thenReturn(new PageImpl<>(List.of(prescription())));
        when(aiProvider.summarizePatientHistory(any())).thenReturn(summaryResponse());
        when(aiProvider.providerName()).thenReturn("mock");

        AiPatientHistorySummaryResponse response = aiClinicalService.summarizePatientHistory(patientId);

        assertThat(response.summary()).contains("documented records");
        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.generatedAt()).isEqualTo(Instant.now(FIXED_CLOCK));
        ArgumentCaptor<AiPatientHistoryInput> inputCaptor = ArgumentCaptor.forClass(AiPatientHistoryInput.class);
        verify(aiProvider).summarizePatientHistory(inputCaptor.capture());
        assertThat(inputCaptor.getValue().patientName()).isEqualTo("Asha Rao");
        assertThat(inputCaptor.getValue().consultations()).first().asString().contains("chief complaint");
        assertThat(inputCaptor.getValue().appointments()).first().asString().contains("03 Sep 2026, 10:00");
        assertThat(inputCaptor.getValue().consultations()).first().asString().contains("03 Sep 2026, 10:00");
        assertThat(inputCaptor.getValue().medicalRecords()).first().asString()
                .contains("03 Sep 2026")
                .doesNotContain("[DEMO:");
    }

    @Test
    void summarizePatientHistoryHandlesEmptyHistory() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(appointmentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(consultationRepository.findByAppointmentPatientId(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(medicalRecordRepository.findByPatientId(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(prescriptionRepository.findByPatientId(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(aiProvider.summarizePatientHistory(any())).thenReturn(new AiPatientHistorySummaryResponse(
                "Insufficient documented clinical history is available in the system.",
                List.of("Not provided"),
                List.of("Not provided"),
                List.of("Not provided"),
                List.of("Not provided"),
                "mock",
                "test-model",
                Instant.now(FIXED_CLOCK),
                "AI-generated summary. Verify against the patient's records."
        ));
        when(aiProvider.providerName()).thenReturn("mock");

        AiPatientHistorySummaryResponse response = aiClinicalService.summarizePatientHistory(patientId);

        assertThat(response.summary()).isEqualTo("No documented clinical history is available.");
        assertThat(response.recentClinicalActivity()).isEmpty();
        assertThat(response.documentedHistory()).isEmpty();
        assertThat(response.recentPrescriptions()).isEmpty();
        assertThat(response.followUp()).isEmpty();
    }

    @Test
    void summarizePatientHistoryRejectsMissingPatient() {
        when(patientService.findPatientEntity(patientId)).thenThrow(new ResourceNotFoundException("Patient not found"));

        assertThatThrownBy(() -> aiClinicalService.summarizePatientHistory(patientId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
        verify(aiProvider, never()).summarizePatientHistory(any());
    }

    @Test
    void summarizePatientHistoryMapsProviderFailureToSafeUnavailableError() {
        when(patientService.findPatientEntity(patientId)).thenReturn(patient);
        when(appointmentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(consultationRepository.findByAppointmentPatientId(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(medicalRecordRepository.findByPatientId(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(prescriptionRepository.findByPatientId(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(aiProvider.summarizePatientHistory(any())).thenThrow(new AiProviderException("provider timeout"));

        assertThatThrownBy(() -> aiClinicalService.summarizePatientHistory(patientId))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("AI assistance is temporarily unavailable. You can continue entering the consultation manually.");
    }

    private AiClinicalService service(AiProperties properties) {
        return new AiClinicalService(
                aiProvider,
                properties,
                consultationRepository,
                patientService,
                appointmentRepository,
                medicalRecordRepository,
                prescriptionRepository,
                FIXED_CLOCK
        );
    }

    private AiConsultationDraftRequest draftRequest() {
        return new AiConsultationDraftRequest(
                "Patient has fever and cough. Advised rest.",
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private AiPatientHistorySummaryResponse summaryResponse() {
        return new AiPatientHistorySummaryResponse(
                "Summary is based only on documented records.",
                List.of("Consultation on 2026-09-03"),
                List.of("Medical record on 2026-09-03"),
                List.of("Prescription on 2026-09-03"),
                List.of("Follow-up: Review in one week"),
                "mock",
                "test-model",
                Instant.now(FIXED_CLOCK),
                "AI-generated summary. Verify against the patient's records."
        );
    }

    private MedicalRecord medicalRecord() {
        MedicalRecord record = new MedicalRecord(
                patient,
                doctor,
                "Synthetic diagnosis",
                "Synthetic symptoms",
                "[DEMO:MEDICAL_RECORD_TEST] Review note documented by clinician",
                LocalDate.of(2026, 9, 3)
        );
        ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
        return record;
    }

    private Prescription prescription() {
        Prescription prescription = new Prescription(patient, doctor, LocalDate.of(2026, 9, 3), "Take after food");
        prescription.addItem(new PrescriptionItem("Synthetic medicine", "10mg", "Once daily", "5 days", "Take after food"));
        ReflectionTestUtils.setField(prescription, "id", UUID.randomUUID());
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
