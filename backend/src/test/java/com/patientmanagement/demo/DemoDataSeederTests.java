package com.patientmanagement.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.Role;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.auth.repository.AuthUserRepository;
import com.patientmanagement.auth.repository.RoleRepository;
import com.patientmanagement.communication.dto.CommunicationCreateRequest;
import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationType;
import com.patientmanagement.communication.repository.CommunicationRepository;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.consultation.model.ConsultationStatus;
import com.patientmanagement.consultation.repository.ConsultationRepository;
import com.patientmanagement.demo.config.DemoDataProperties;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.repository.DoctorRepository;
import com.patientmanagement.feedback.model.Feedback;
import com.patientmanagement.feedback.repository.FeedbackRepository;
import com.patientmanagement.medicalrecord.model.MedicalRecord;
import com.patientmanagement.medicalrecord.repository.MedicalRecordRepository;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.repository.PatientRepository;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTests {

    private static final String TEST_DEMO_PASSWORD = "test-only-demo-password";
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-04T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private CommunicationRepository communicationRepository;

    @Mock
    private CommunicationService communicationService;

    @Mock
    private FeedbackRepository feedbackRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, AuthUser> users = new LinkedHashMap<>();
    private final Map<String, Patient> patients = new LinkedHashMap<>();
    private final Map<String, Doctor> doctors = new LinkedHashMap<>();
    private final Map<String, Appointment> appointments = new LinkedHashMap<>();
    private final Map<UUID, Consultation> consultations = new LinkedHashMap<>();
    private final Map<String, MedicalRecord> medicalRecords = new LinkedHashMap<>();
    private final Map<String, Prescription> prescriptions = new LinkedHashMap<>();
    private final Set<String> communicationKeys = new java.util.HashSet<>();
    private final Map<UUID, Feedback> feedback = new LinkedHashMap<>();

    @Test
    void disabledSeedDoesNothing() {
        DemoDataSeeder seeder = seeder(false);

        DemoDataSeeder.SeedCounts counts = seeder.seed();

        assertThat(counts).isEqualTo(DemoDataSeeder.SeedCounts.empty());
        verifyNoInteractions(roleRepository);
        assertThat(users).isEmpty();
        assertThat(patients).isEmpty();
        assertThat(doctors).isEmpty();
        assertThat(appointments).isEmpty();
        assertThat(consultations).isEmpty();
        assertThat(medicalRecords).isEmpty();
        assertThat(prescriptions).isEmpty();
        assertThat(communicationKeys).isEmpty();
        assertThat(feedback).isEmpty();
    }

    @Test
    void enabledSeedCreatesExpectedDemoDataWithHashedPasswordsAndRoles() {
        stubAll();

        DemoDataSeeder.SeedCounts counts = seeder(true).seed();

        assertThat(counts).isEqualTo(new DemoDataSeeder.SeedCounts(6, 10, 4, 18, 6, 6, 4, 17, 4));
        assertThat(users).containsOnlyKeys(
                DemoDataSeeder.ADMIN_EMAIL,
                DemoDataSeeder.DOCTOR_EMAIL,
                DemoDataSeeder.SECONDARY_DOCTOR_EMAIL,
                DemoDataSeeder.TERTIARY_DOCTOR_EMAIL,
                DemoDataSeeder.QUATERNARY_DOCTOR_EMAIL,
                DemoDataSeeder.RECEPTIONIST_EMAIL
        );
        assertDemoUser(DemoDataSeeder.ADMIN_EMAIL, RoleName.ADMIN);
        assertDemoUser(DemoDataSeeder.DOCTOR_EMAIL, RoleName.DOCTOR);
        assertDemoUser(DemoDataSeeder.SECONDARY_DOCTOR_EMAIL, RoleName.DOCTOR);
        assertDemoUser(DemoDataSeeder.TERTIARY_DOCTOR_EMAIL, RoleName.DOCTOR);
        assertDemoUser(DemoDataSeeder.QUATERNARY_DOCTOR_EMAIL, RoleName.DOCTOR);
        assertDemoUser(DemoDataSeeder.RECEPTIONIST_EMAIL, RoleName.RECEPTIONIST);

        assertThat(patients).containsOnlyKeys(
                "asha.demo.patient@example.com", "noah.demo.patient@example.com", "ira.demo.patient@example.com",
                "meera.demo.patient@example.com", "ethan.demo.patient@example.com", "sofia.demo.patient@example.com",
                "liam.demo.patient@example.com", "priya.demo.patient@example.com", "lucas.demo.patient@example.com",
                "zara.demo.patient@example.com"
        );
        assertThat(doctors).containsOnlyKeys("CLN-GEN-1001", "CLN-FAM-1002", "CLN-CAR-1003", "CLN-PED-1004");
        assertThat(doctors.get("CLN-GEN-1001").getUser().getUsername()).isEqualTo(DemoDataSeeder.DOCTOR_EMAIL);
        assertThat(doctors.get("CLN-FAM-1002").getUser().getUsername())
                .isEqualTo(DemoDataSeeder.SECONDARY_DOCTOR_EMAIL);

        Appointment activeAppointment = appointment(DemoDataSeeder.ACTIVE_CONSULTATION_MARKER);
        Appointment completedAppointment = appointment(DemoDataSeeder.COMPLETED_CONSULTATION_MARKER);
        assertThat(activeAppointment.getPatient().getEmail()).isEqualTo("asha.demo.patient@example.com");
        assertThat(activeAppointment.getDoctor().getLicenseNumber()).isEqualTo("CLN-GEN-1001");
        assertThat(activeAppointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(activeAppointment.getAppointmentDateTime()).isAfter(FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime());
        assertThat(activeAppointment.getNotes()).contains("mild tension-type headache");

        Consultation activeConsultation = consultations.get(activeAppointment.getId());
        Consultation completedConsultation = consultations.get(completedAppointment.getId());
        assertThat(activeConsultation.getStatus()).isEqualTo(ConsultationStatus.IN_PROGRESS);
        assertThat(activeConsultation.getAppointment()).isSameAs(activeAppointment);
        assertThat(activeConsultation.getSymptoms()).contains("Mild bilateral headache");
        assertThat(completedConsultation.getStatus()).isEqualTo(ConsultationStatus.COMPLETED);
        assertThat(completedConsultation.getAppointment()).isSameAs(completedAppointment);
        assertThat(completedAppointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);

        assertThat(medicalRecords.values())
                .extracting(record -> record.getPatient().getEmail())
                .contains("asha.demo.patient@example.com", "noah.demo.patient@example.com");
        assertThat(prescriptions.values()).hasSize(4);
        assertThat(prescriptions.values()).anySatisfy(prescription -> {
            assertThat(prescription.getPatient().getEmail()).isEqualTo("asha.demo.patient@example.com");
            assertThat(prescription.getDoctor().getLicenseNumber()).isEqualTo("CLN-GEN-1001");
            assertThat(prescription.getItems()).hasSize(2);
            assertThat(prescription.getItems()).extracting(item -> item.getMedicineName())
                    .containsExactly("Paracetamol", "Saline nasal spray");
        });
        assertThat(communicationKeys).contains(
                key(activeAppointment, CommunicationType.APPOINTMENT_CONFIRMATION),
                key(appointment(DemoDataSeeder.REMINDER_APPOINTMENT_MARKER), CommunicationType.APPOINTMENT_REMINDER),
                key(completedAppointment, CommunicationType.CONSULTATION_COMPLETED),
                "prescription:" + prescriptions.values().iterator().next().getId()
        );
        assertThat(feedback).hasSize(4);
    }

    @Test
    void enabledSeedRequiresAnExplicitPassword() {
        DemoDataSeeder seeder = seeder(true, "");

        assertThatThrownBy(seeder::seed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DEMO_DATA_PASSWORD must be set when demo data is enabled");
        verifyNoInteractions(roleRepository);
    }

    @Test
    void rerunningSeedDoesNotCreateDuplicateDemoRecords() {
        stubAll();
        DemoDataSeeder seeder = seeder(true);

        seeder.seed();
        Appointment activeAppointment = appointment(DemoDataSeeder.ACTIVE_CONSULTATION_MARKER);
        Consultation activeConsultation = consultations.get(activeAppointment.getId());
        activeConsultation.complete(FIXED_CLOCK.instant().minusSeconds(60));
        activeAppointment.setStatus(AppointmentStatus.COMPLETED);
        DemoDataSeeder.SeedCounts secondRun = seeder.seed();

        assertThat(secondRun).isEqualTo(new DemoDataSeeder.SeedCounts(0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThat(users).hasSize(6);
        assertThat(patients).hasSize(10);
        assertThat(doctors).hasSize(4);
        assertThat(appointments).hasSize(18);
        assertThat(consultations).hasSize(6);
        assertThat(medicalRecords).hasSize(6);
        assertThat(prescriptions).hasSize(4);
        assertThat(communicationKeys).hasSize(17);
        assertThat(feedback).hasSize(4);
        assertDemoUser(DemoDataSeeder.ADMIN_EMAIL, RoleName.ADMIN);
        assertDemoUser(DemoDataSeeder.DOCTOR_EMAIL, RoleName.DOCTOR);
        assertDemoUser(DemoDataSeeder.SECONDARY_DOCTOR_EMAIL, RoleName.DOCTOR);
        assertDemoUser(DemoDataSeeder.TERTIARY_DOCTOR_EMAIL, RoleName.DOCTOR);
        assertDemoUser(DemoDataSeeder.QUATERNARY_DOCTOR_EMAIL, RoleName.DOCTOR);
        assertDemoUser(DemoDataSeeder.RECEPTIONIST_EMAIL, RoleName.RECEPTIONIST);
        assertThat(activeAppointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(activeConsultation.getStatus()).isEqualTo(ConsultationStatus.IN_PROGRESS);
        assertThat(activeConsultation.getCompletedAt()).isNull();
    }

    @Test
    void defersDemoCommunicationsUntilTheSeedTransactionCommits() {
        stubAll();
        DemoDataSeeder seeder = seeder(true);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            seeder.seed();

            verify(communicationService, never()).createAppointmentConfirmation(any(Appointment.class));
            verify(communicationService, never()).createPrescriptionAvailable(any(Prescription.class));
            verify(communicationService, atLeastOnce()).createAppointmentReminder(any(Appointment.class));
            verify(communicationService, atLeastOnce()).createCommunication(any(CommunicationCreateRequest.class));

            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> {
                synchronization.afterCommit();
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            });

            verify(communicationService, atLeastOnce()).createPrescriptionAvailable(any(Prescription.class));
            verify(communicationService, atLeastOnce()).createAppointmentConfirmation(any(Appointment.class));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void assertDemoUser(String username, RoleName roleName) {
        AuthUser user = users.get(username);
        assertThat(user).isNotNull();
        assertThat(user.getPasswordHash()).isNotEqualTo(TEST_DEMO_PASSWORD);
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(TEST_DEMO_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getRoles()).extracting(Role::getName).containsExactly(roleName);
        assertThat(user.isEnabled()).isTrue();
    }

    private Appointment appointment(String marker) {
        return appointments.values().stream()
                .filter(appointment -> appointment.getNotes().contains(marker))
                .findFirst()
                .orElseThrow();
    }

    private DemoDataSeeder seeder(boolean enabled) {
        return seeder(enabled, TEST_DEMO_PASSWORD);
    }

    private DemoDataSeeder seeder(boolean enabled, String password) {
        return new DemoDataSeeder(
                new DemoDataProperties(enabled, password),
                authUserRepository,
                roleRepository,
                passwordEncoder,
                patientRepository,
                doctorRepository,
                appointmentRepository,
                consultationRepository,
                medicalRecordRepository,
                prescriptionRepository,
                communicationRepository,
                communicationService,
                feedbackRepository,
                FIXED_CLOCK
        );
    }

    private void stubAll() {
        stubRoles();
        stubUsers();
        stubPatients();
        stubDoctors();
        stubAppointments();
        stubConsultations();
        stubMedicalRecords();
        stubPrescriptions();
        stubCommunications();
        stubFeedback();
    }

    private void stubRoles() {
        Map<RoleName, Role> roles = new EnumMap<>(RoleName.class);
        for (RoleName roleName : RoleName.values()) {
            roles.put(roleName, new Role(roleName));
        }
        when(roleRepository.findByName(any(RoleName.class))).thenAnswer(invocation ->
                Optional.of(roles.get(invocation.getArgument(0)))
        );
    }

    private void stubUsers() {
        when(authUserRepository.findByUsernameIgnoreCase(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(users.get(invocation.getArgument(0, String.class).toLowerCase()))
        );
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser user = invocation.getArgument(0);
            assignId(user, "user:" + user.getUsername());
            users.put(user.getUsername().toLowerCase(), user);
            return user;
        });
    }

    private void stubPatients() {
        when(patientRepository.findByEmailIgnoreCase(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(patients.get(invocation.getArgument(0, String.class).toLowerCase()))
        );
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> {
            Patient patient = invocation.getArgument(0);
            assignId(patient, "patient:" + patient.getEmail());
            patients.put(patient.getEmail().toLowerCase(), patient);
            return patient;
        });
    }

    private void stubDoctors() {
        when(doctorRepository.findByUserId(any(UUID.class))).thenAnswer(invocation ->
                doctors.values().stream()
                        .filter(doctor -> doctor.getUser() != null
                                && doctor.getUser().getId().equals(invocation.getArgument(0)))
                        .findFirst()
        );
        when(doctorRepository.findByLicenseNumber(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(doctors.get(invocation.getArgument(0, String.class)))
        );
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> {
            Doctor doctor = invocation.getArgument(0);
            assignId(doctor, "doctor:" + doctor.getLicenseNumber());
            doctors.put(doctor.getLicenseNumber(), doctor);
            return doctor;
        });
    }

    private void stubAppointments() {
        when(appointmentRepository.findFirstByNotesContaining(anyString())).thenAnswer(invocation -> {
            String marker = invocation.getArgument(0);
            return appointments.values().stream()
                    .filter(appointment -> appointment.getNotes() != null && appointment.getNotes().contains(marker))
                    .findFirst();
        });
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            assignId(appointment, "appointment:" + appointment.getNotes());
            appointments.put(appointment.getNotes(), appointment);
            return appointment;
        });
    }

    private void stubConsultations() {
        when(consultationRepository.findByAppointmentId(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(consultations.get(invocation.getArgument(0)))
        );
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(invocation -> {
            Consultation consultation = invocation.getArgument(0);
            assignId(consultation, "consultation:" + consultation.getAppointment().getId());
            consultations.put(consultation.getAppointment().getId(), consultation);
            return consultation;
        });
    }

    private void stubMedicalRecords() {
        when(medicalRecordRepository.findFirstByNotesContaining(anyString())).thenAnswer(invocation -> {
            String marker = invocation.getArgument(0);
            return medicalRecords.values().stream()
                    .filter(record -> record.getNotes() != null && record.getNotes().contains(marker))
                    .findFirst();
        });
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> {
            MedicalRecord record = invocation.getArgument(0);
            assignId(record, "medical-record:" + record.getNotes());
            medicalRecords.put(record.getNotes(), record);
            return record;
        });
    }

    private void stubPrescriptions() {
        when(prescriptionRepository.findFirstByNotesContaining(anyString())).thenAnswer(invocation -> {
            String marker = invocation.getArgument(0);
            return prescriptions.values().stream()
                    .filter(prescription -> prescription.getNotes() != null && prescription.getNotes().contains(marker))
                    .findFirst();
        });
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> {
            Prescription prescription = invocation.getArgument(0);
            assignId(prescription, "prescription:" + prescription.getNotes());
            prescriptions.put(prescription.getNotes(), prescription);
            return prescription;
        });
    }

    private void stubCommunications() {
        when(communicationRepository.existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                any(UUID.class),
                any(UUID.class),
                any(CommunicationType.class),
                any(CommunicationChannel.class),
                any()
        )).thenAnswer(invocation -> communicationKeys.contains(
                invocation.getArgument(1, UUID.class) + ":" + invocation.getArgument(2, CommunicationType.class)
        ));
        when(communicationService.createAppointmentConfirmation(any(Appointment.class))).thenAnswer(invocation -> {
            rememberCommunication(key(invocation.getArgument(0), CommunicationType.APPOINTMENT_CONFIRMATION));
            return null;
        });
        when(communicationService.createAppointmentReminder(any(Appointment.class))).thenAnswer(invocation -> {
            rememberCommunication(key(invocation.getArgument(0), CommunicationType.APPOINTMENT_REMINDER));
            return null;
        });
        when(communicationService.createCommunication(any(CommunicationCreateRequest.class))).thenAnswer(invocation -> {
            CommunicationCreateRequest request = invocation.getArgument(0);
            rememberCommunication(request.appointmentId() + ":" + request.type());
            return null;
        });
        when(communicationService.createPrescriptionAvailable(any(Prescription.class))).thenAnswer(invocation -> {
            Prescription prescription = invocation.getArgument(0);
            rememberCommunication("prescription:" + prescription.getId());
            return null;
        });
    }

    private void stubFeedback() {
        when(feedbackRepository.findByConsultationId(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(feedback.get(invocation.getArgument(0)))
        );
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> {
            Feedback item = invocation.getArgument(0);
            assignId(item, "feedback:" + item.getConsultation().getId());
            feedback.put(item.getConsultation().getId(), item);
            return item;
        });
    }

    private void rememberCommunication(String key) {
        if (!communicationKeys.add(key)) {
            throw new DuplicateResourceException("Communication already exists for this patient and appointment");
        }
    }

    private String key(Appointment appointment, CommunicationType type) {
        return appointment.getId() + ":" + type;
    }

    private void assignId(Object entity, String seed) {
        ReflectionTestUtils.setField(
                entity,
                "id",
                UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))
        );
    }
}
