package com.patientmanagement.ai.service;

import com.patientmanagement.ai.config.AiProperties;
import com.patientmanagement.ai.dto.AiConsultationDraft;
import com.patientmanagement.ai.dto.AiConsultationDraftRequest;
import com.patientmanagement.ai.dto.AiConsultationDraftResponse;
import com.patientmanagement.ai.dto.AiPatientHistorySummaryResponse;
import com.patientmanagement.ai.provider.AiConsultationDraftInput;
import com.patientmanagement.ai.provider.AiProviderMalformedResponseException;
import com.patientmanagement.ai.provider.AiPatientHistoryInput;
import com.patientmanagement.ai.provider.AiProvider;
import com.patientmanagement.ai.provider.AiProviderException;
import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.consultation.repository.ConsultationRepository;
import com.patientmanagement.demo.security.DemoAiInputLimiter;
import com.patientmanagement.medicalrecord.model.MedicalRecord;
import com.patientmanagement.medicalrecord.repository.MedicalRecordRepository;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.model.PrescriptionItem;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiClinicalService {

    private static final int HISTORY_LIMIT = 5;
    private static final int MAX_OUTPUT_LENGTH = 4000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH);
    private static final String CONSULTATION_NOTICE =
            "AI-generated draft - review before saving.";
    private static final String SUMMARY_NOTICE =
            "AI-generated summary. Verify against the patient's records.";
    private static final String UNAVAILABLE_MESSAGE =
            "AI assistance is temporarily unavailable. You can continue entering the consultation manually.";
    private static final String MALFORMED_MESSAGE =
            "AI response could not be processed. You can continue entering the consultation manually.";

    private final AiProvider aiProvider;
    private final AiProperties aiProperties;
    private final ConsultationRepository consultationRepository;
    private final PatientService patientService;
    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ClinicalAccessService clinicalAccessService;
    private final Clock clock;
    private final DemoAiInputLimiter demoAiInputLimiter;

    public AiClinicalService(
            AiProvider aiProvider,
            AiProperties aiProperties,
            ConsultationRepository consultationRepository,
            PatientService patientService,
            AppointmentRepository appointmentRepository,
            MedicalRecordRepository medicalRecordRepository,
            PrescriptionRepository prescriptionRepository,
            ClinicalAccessService clinicalAccessService
    ) {
        this(
                aiProvider,
                aiProperties,
                consultationRepository,
                patientService,
                appointmentRepository,
                medicalRecordRepository,
                prescriptionRepository,
                clinicalAccessService,
                Clock.systemDefaultZone(),
                null
        );
    }

    AiClinicalService(
            AiProvider aiProvider,
            AiProperties aiProperties,
            ConsultationRepository consultationRepository,
            PatientService patientService,
            AppointmentRepository appointmentRepository,
            MedicalRecordRepository medicalRecordRepository,
            PrescriptionRepository prescriptionRepository,
            Clock clock
    ) {
        this(
                aiProvider,
                aiProperties,
                consultationRepository,
                patientService,
                appointmentRepository,
                medicalRecordRepository,
                prescriptionRepository,
                null,
                clock,
                null
        );
    }

    AiClinicalService(
            AiProvider aiProvider,
            AiProperties aiProperties,
            ConsultationRepository consultationRepository,
            PatientService patientService,
            AppointmentRepository appointmentRepository,
            MedicalRecordRepository medicalRecordRepository,
            PrescriptionRepository prescriptionRepository,
            ClinicalAccessService clinicalAccessService,
            Clock clock
    ) {
        this(
                aiProvider,
                aiProperties,
                consultationRepository,
                patientService,
                appointmentRepository,
                medicalRecordRepository,
                prescriptionRepository,
                clinicalAccessService,
                clock,
                null
        );
    }

    @Autowired
    AiClinicalService(
            AiProvider aiProvider,
            AiProperties aiProperties,
            ConsultationRepository consultationRepository,
            PatientService patientService,
            AppointmentRepository appointmentRepository,
            MedicalRecordRepository medicalRecordRepository,
            PrescriptionRepository prescriptionRepository,
            ClinicalAccessService clinicalAccessService,
            Clock clock,
            DemoAiInputLimiter demoAiInputLimiter
    ) {
        this.aiProvider = aiProvider;
        this.aiProperties = aiProperties;
        this.consultationRepository = consultationRepository;
        this.patientService = patientService;
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.clinicalAccessService = clinicalAccessService;
        this.clock = clock;
        this.demoAiInputLimiter = demoAiInputLimiter;
    }

    @Transactional(readOnly = true)
    public AiConsultationDraftResponse generateConsultationDraft(UUID consultationId, AiConsultationDraftRequest request) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new com.patientmanagement.common.exception.ResourceNotFoundException("Consultation not found"));
        requireConsultationAccess(consultation);
        ensureEnabled();
        if (demoAiInputLimiter != null) {
            demoAiInputLimiter.requireDraftWithinLimit(request);
        }

        AiConsultationDraft draft = callProvider(() -> aiProvider.generateConsultationDraft(new AiConsultationDraftInput(
                nullableTrim(request.roughNotes()),
                nullableTrim(request.chiefComplaint()),
                nullableTrim(request.symptoms()),
                nullableTrim(request.examination()),
                nullableTrim(request.assessment()),
                nullableTrim(request.treatment()),
                nullableTrim(request.followUpInstructions())
        )));
        validateDraft(draft);

        return new AiConsultationDraftResponse(
                draft,
                aiProvider.providerName(),
                aiProperties.model(),
                Instant.now(clock),
                CONSULTATION_NOTICE
        );
    }

    @Transactional(readOnly = true)
    public AiPatientHistorySummaryResponse summarizePatientHistory(UUID patientId) {
        requirePatientAccess(patientId);
        Patient patient = patientService.findPatientEntity(patientId);
        ensureEnabled();

        List<String> appointments = appointmentSummaries(patientId);
        List<String> consultations = consultationSummaries(patientId);
        List<String> medicalRecords = medicalRecordSummaries(patientId);
        List<String> prescriptions = prescriptionSummaries(patientId);
        boolean hasDocumentedHistory = !appointments.isEmpty()
                || !consultations.isEmpty()
                || !medicalRecords.isEmpty()
                || !prescriptions.isEmpty();

        AiPatientHistoryInput input = new AiPatientHistoryInput(
                patient.getFirstName() + " " + patient.getLastName(),
                patient.getDateOfBirth(),
                patient.getGender().name(),
                appointments,
                consultations,
                medicalRecords,
                prescriptions
        );
        if (demoAiInputLimiter != null) {
            demoAiInputLimiter.requireSummaryWithinLimit(input);
        }

        AiPatientHistorySummaryResponse response = callProvider(() -> aiProvider.summarizePatientHistory(input));
        validateSummary(response);
        return new AiPatientHistorySummaryResponse(
                hasDocumentedHistory ? response.summary() : "No documented clinical history is available.",
                bounded(response.recentClinicalActivity()),
                bounded(response.documentedHistory()),
                bounded(response.recentPrescriptions()),
                bounded(response.followUp()),
                aiProvider.providerName(),
                aiProperties.model(),
                Instant.now(clock),
                SUMMARY_NOTICE
        );
    }

    private List<String> appointmentSummaries(UUID patientId) {
        return appointmentRepository.findAll(
                        patientAppointmentSpecification(patientId),
                        PageRequest.of(0, HISTORY_LIMIT, Sort.by(Sort.Direction.DESC, "appointmentDateTime"))
                )
                .getContent()
                .stream()
                .map(this::appointmentSummary)
                .toList();
    }

    private List<String> consultationSummaries(UUID patientId) {
        return consultationRepository.findByAppointmentPatientId(
                        patientId,
                        PageRequest.of(0, HISTORY_LIMIT, Sort.by(Sort.Direction.DESC, "startedAt"))
                )
                .getContent()
                .stream()
                .map(this::consultationSummary)
                .toList();
    }

    private List<String> medicalRecordSummaries(UUID patientId) {
        return medicalRecordRepository.findByPatientId(
                        patientId,
                        PageRequest.of(0, HISTORY_LIMIT, Sort.by(Sort.Direction.DESC, "recordDate"))
                )
                .getContent()
                .stream()
                .map(this::medicalRecordSummary)
                .toList();
    }

    private List<String> prescriptionSummaries(UUID patientId) {
        return prescriptionRepository.findByPatientId(
                        patientId,
                        PageRequest.of(0, HISTORY_LIMIT, Sort.by(Sort.Direction.DESC, "prescriptionDate"))
                )
                .getContent()
                .stream()
                .map(this::prescriptionSummary)
                .toList();
    }

    private Specification<Appointment> patientAppointmentSpecification(UUID patientId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("patient").get("id"), patientId));
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("appointmentDateTime"), LocalDateTime.now(clock)));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String appointmentSummary(Appointment appointment) {
        return "Appointment on " + DATE_TIME_FORMATTER.format(appointment.getAppointmentDateTime())
                + " with Dr. " + appointment.getDoctor().getFirstName() + " " + appointment.getDoctor().getLastName()
                + " for " + visibleText(appointment.getReason())
                + " (" + displayEnum(appointment.getStatus().name()) + ")";
    }

    private String consultationSummary(Consultation consultation) {
        List<String> parts = new ArrayList<>();
        parts.add("Consultation on " + DATE_TIME_FORMATTER.format(consultation.getStartedAt().atZone(clock.getZone())));
        addIfPresent(parts, "chief complaint", consultation.getChiefComplaint());
        addIfPresent(parts, "symptoms", consultation.getSymptoms());
        addIfPresent(parts, "assessment", consultation.getAssessment());
        addIfPresent(parts, "follow-up", consultation.getFollowUpInstructions());
        return String.join("; ", parts);
    }

    private String medicalRecordSummary(MedicalRecord record) {
        List<String> parts = new ArrayList<>();
        parts.add("Medical record on " + DATE_FORMATTER.format(record.getRecordDate()));
        addIfPresent(parts, "diagnosis", record.getDiagnosis());
        addIfPresent(parts, "symptoms", record.getSymptoms());
        addIfPresent(parts, "notes", record.getNotes());
        return String.join("; ", parts);
    }

    private String prescriptionSummary(Prescription prescription) {
        String items = prescription.getItems().stream()
                .map(this::prescriptionItemSummary)
                .reduce((left, right) -> left + ", " + right)
                .orElse("no medicine items recorded");
        return "Prescription on " + DATE_FORMATTER.format(prescription.getPrescriptionDate()) + ": " + items;
    }

    private String prescriptionItemSummary(PrescriptionItem item) {
        List<String> parts = new ArrayList<>();
        parts.add(item.getMedicineName());
        addIfPresent(parts, "dosage", item.getDosage());
        addIfPresent(parts, "frequency", item.getFrequency());
        addIfPresent(parts, "duration", item.getDuration());
        addIfPresent(parts, "instructions", item.getInstructions());
        return String.join(" ", parts);
    }

    private void addIfPresent(List<String> parts, String label, String value) {
        String trimmed = nullableTrim(value);
        if (trimmed != null) {
            String visible = visibleText(trimmed);
            if (!visible.isBlank()) {
                parts.add(label + ": " + visible);
            }
        }
    }

    private void ensureEnabled() {
        if (!aiProperties.enabled()) {
            throw new AiServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    private void requireConsultationAccess(Consultation consultation) {
        if (clinicalAccessService != null) {
            clinicalAccessService.requireConsultationAccess(consultation);
        }
    }

    private void requirePatientAccess(UUID patientId) {
        if (clinicalAccessService != null) {
            clinicalAccessService.requirePatientAccess(patientId);
        }
    }

    private <T> T callProvider(ProviderCall<T> providerCall) {
        try {
            return providerCall.execute();
        } catch (AiProviderResponseException exception) {
            throw exception;
        } catch (AiProviderMalformedResponseException exception) {
            throw new AiProviderResponseException(MALFORMED_MESSAGE);
        } catch (AiProviderException exception) {
            throw new AiServiceUnavailableException(UNAVAILABLE_MESSAGE);
        } catch (RuntimeException exception) {
            throw new AiServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    private void validateDraft(AiConsultationDraft draft) {
        if (draft == null
                || invalidOutput(draft.chiefComplaint())
                || invalidOutput(draft.symptoms())
                || invalidOutput(draft.examination())
                || invalidOutput(draft.assessment())
                || invalidOutput(draft.treatmentAdvice())
                || invalidOutput(draft.followUpInstructions())) {
            throw new AiProviderResponseException(MALFORMED_MESSAGE);
        }
    }

    private void validateSummary(AiPatientHistorySummaryResponse response) {
        if (response == null
                || invalidOutput(response.summary())
                || response.recentClinicalActivity() == null
                || response.documentedHistory() == null
                || response.recentPrescriptions() == null
                || response.followUp() == null) {
            throw new AiProviderResponseException(MALFORMED_MESSAGE);
        }
    }

    private boolean invalidOutput(String value) {
        return value == null || value.isBlank() || value.length() > outputLimit();
    }

    private List<String> bounded(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::visibleText)
                .filter(value -> !value.equalsIgnoreCase("Not provided"))
                .map(value -> value.length() > outputLimit() ? value.substring(0, outputLimit()) : value)
                .limit(HISTORY_LIMIT)
                .toList();
    }

    private String visibleText(String value) {
        String visible = value.replaceFirst("^\\s*\\[DEMO:[^]]+]\\s*", "").trim();
        String lower = visible.toLowerCase(Locale.ENGLISH);
        if (lower.contains("demo-only")
                || lower.contains("development-only")
                || lower.contains("fictional clinical note")) {
            return "";
        }
        return visible;
    }

    private int outputLimit() {
        return demoAiInputLimiter == null
                ? MAX_OUTPUT_LENGTH
                : Math.min(MAX_OUTPUT_LENGTH, demoAiInputLimiter.maxOutputCharacters());
    }

    private String displayEnum(String value) {
        String[] parts = value.toLowerCase(Locale.ENGLISH).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String nullableTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        T execute();
    }
}
