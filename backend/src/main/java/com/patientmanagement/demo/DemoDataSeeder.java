package com.patientmanagement.demo;

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
import com.patientmanagement.communication.model.CommunicationStatus;
import com.patientmanagement.communication.model.CommunicationType;
import com.patientmanagement.communication.repository.CommunicationRepository;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.consultation.repository.ConsultationRepository;
import com.patientmanagement.demo.config.DemoDataProperties;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.repository.DoctorRepository;
import com.patientmanagement.feedback.model.Feedback;
import com.patientmanagement.feedback.repository.FeedbackRepository;
import com.patientmanagement.medicalrecord.model.MedicalRecord;
import com.patientmanagement.medicalrecord.repository.MedicalRecordRepository;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.repository.PatientRepository;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.model.PrescriptionItem;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "demo.data", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String ADMIN_EMAIL = "admin.demo@example.com";
    public static final String DOCTOR_EMAIL = "doctor.demo@example.com";
    public static final String SECONDARY_DOCTOR_EMAIL = "doctor.owen.demo@example.com";
    public static final String TERTIARY_DOCTOR_EMAIL = "doctor.leena.demo@example.com";
    public static final String QUATERNARY_DOCTOR_EMAIL = "doctor.daniel.demo@example.com";
    public static final String RECEPTIONIST_EMAIL = "receptionist.demo@example.com";
    public static final String ACTIVE_CONSULTATION_MARKER = "[DEMO:ACTIVE_CONSULTATION]";
    public static final String COMPLETED_CONSULTATION_MARKER = "[DEMO:COMPLETED_CONSULTATION]";
    public static final String CANCELLED_APPOINTMENT_MARKER = "[DEMO:CANCELLED_APPOINTMENT]";
    public static final String REMINDER_APPOINTMENT_MARKER = "[DEMO:REMINDER_APPOINTMENT]";
    public static final String MEDICAL_RECORD_PRIMARY_MARKER = "[DEMO:MEDICAL_RECORD_PRIMARY]";
    public static final String MEDICAL_RECORD_SECONDARY_MARKER = "[DEMO:MEDICAL_RECORD_SECONDARY]";
    public static final String PRESCRIPTION_MARKER = "[DEMO:PRESCRIPTION_AVAILABLE]";

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final Set<CommunicationStatus> ACTIVE_COMMUNICATION_STATUSES =
            EnumSet.of(CommunicationStatus.PENDING, CommunicationStatus.SENT, CommunicationStatus.DELIVERED,
                    CommunicationStatus.SIMULATED, CommunicationStatus.DISABLED);

    private final DemoDataProperties properties;
    private final AuthUserRepository authUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final CommunicationRepository communicationRepository;
    private final CommunicationService communicationService;
    private final FeedbackRepository feedbackRepository;
    private final Clock clock;

    @Autowired
    public DemoDataSeeder(
            DemoDataProperties properties,
            AuthUserRepository authUserRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            ConsultationRepository consultationRepository,
            MedicalRecordRepository medicalRecordRepository,
            PrescriptionRepository prescriptionRepository,
            CommunicationRepository communicationRepository,
            CommunicationService communicationService,
            FeedbackRepository feedbackRepository
    ) {
        this(
                properties,
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
                Clock.systemDefaultZone()
        );
    }

    DemoDataSeeder(
            DemoDataProperties properties,
            AuthUserRepository authUserRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            ConsultationRepository consultationRepository,
            MedicalRecordRepository medicalRecordRepository,
            PrescriptionRepository prescriptionRepository,
            CommunicationRepository communicationRepository,
            CommunicationService communicationService,
            FeedbackRepository feedbackRepository,
            Clock clock
    ) {
        this.properties = properties;
        this.authUserRepository = authUserRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.communicationRepository = communicationRepository;
        this.communicationService = communicationService;
        this.feedbackRepository = feedbackRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SeedCounts counts = seed();
        LOGGER.info(
                "Development demo data seed completed: users={}, patients={}, doctors={}, appointments={}, "
                        + "consultations={}, medicalRecords={}, prescriptions={}, communicationsRequested={}, feedback={}",
                counts.users(),
                counts.patients(),
                counts.doctors(),
                counts.appointments(),
                counts.consultations(),
                counts.medicalRecords(),
                counts.prescriptions(),
                counts.communicationsRequested(),
                counts.feedback()
        );
    }

    SeedCounts seed() {
        if (!properties.enabled()) {
            return SeedCounts.empty();
        }

        String demoPassword = requiredDemoPassword();
        SeedCounter counter = new SeedCounter();
        Map<RoleName, Role> roles = roles();
        ensureUser(ADMIN_EMAIL, "Anika", "Mehta", roles.get(RoleName.ADMIN), demoPassword, counter);
        AuthUser primaryDoctorUser = ensureUser(
                DOCTOR_EMAIL,
                "Maya",
                "Patel",
                roles.get(RoleName.DOCTOR),
                demoPassword,
                counter
        );
        AuthUser secondaryDoctorUser = ensureUser(
                SECONDARY_DOCTOR_EMAIL,
                "Owen",
                "Brooks",
                roles.get(RoleName.DOCTOR),
                demoPassword,
                counter
        );
        AuthUser tertiaryDoctorUser = ensureUser(
                TERTIARY_DOCTOR_EMAIL,
                "Leena",
                "Shah",
                roles.get(RoleName.DOCTOR),
                demoPassword,
                counter
        );
        AuthUser quaternaryDoctorUser = ensureUser(
                QUATERNARY_DOCTOR_EMAIL,
                "Daniel",
                "Kim",
                roles.get(RoleName.DOCTOR),
                demoPassword,
                counter
        );
        ensureUser(
                RECEPTIONIST_EMAIL,
                "Aditi",
                "Verma",
                roles.get(RoleName.RECEPTIONIST),
                demoPassword,
                counter
        );

        Patient patientAsha = ensurePatient(
                "Asha",
                "Rao",
                LocalDate.of(1990, 4, 12),
                PatientGender.FEMALE,
                BloodGroup.O_POSITIVE,
                "+15555550100",
                "asha.demo.patient@example.com",
                "18 Willow Park, Brookfield",
                "Ravi Rao",
                "+15555550101",
                counter
        );
        Patient patientNoah = ensurePatient(
                "Noah",
                "Bennett",
                LocalDate.of(1984, 11, 5),
                PatientGender.MALE,
                BloodGroup.A_POSITIVE,
                "+15555550110",
                "noah.demo.patient@example.com",
                "42 Cedar Avenue, Lakeside",
                "Mira Bennett",
                "+15555550111",
                counter
        );
        Patient patientIra = ensurePatient(
                "Ira",
                "Morgan",
                LocalDate.of(2001, 2, 20),
                PatientGender.OTHER,
                BloodGroup.B_POSITIVE,
                "+15555550120",
                "ira.demo.patient@example.com",
                "7 Orchard Street, Westhaven",
                "Sam Morgan",
                "+15555550121",
                counter
        );
        Patient patientMeera = ensurePatient(
                "Meera", "Iyer", LocalDate.of(1966, 8, 19), PatientGender.FEMALE, BloodGroup.AB_POSITIVE,
                "+15555550130", "meera.demo.patient@example.com", "91 Lake Road, Fairview",
                "Arun Iyer", "+15555550131", counter
        );
        Patient patientEthan = ensurePatient(
                "Ethan", "Clarke", LocalDate.of(2014, 3, 7), PatientGender.MALE, BloodGroup.O_NEGATIVE,
                "+15555550140", "ethan.demo.patient@example.com", "26 Maple Close, Hillcrest",
                "Nora Clarke", "+15555550141", counter
        );
        Patient patientSofia = ensurePatient(
                "Sofia", "Martinez", LocalDate.of(1977, 12, 2), PatientGender.FEMALE, BloodGroup.A_NEGATIVE,
                "+15555550150", "sofia.demo.patient@example.com", "54 River Walk, Northfield",
                "Luis Martinez", "+15555550151", counter
        );
        Patient patientLiam = ensurePatient(
                "Liam", "Chen", LocalDate.of(1995, 6, 24), PatientGender.MALE, BloodGroup.B_NEGATIVE,
                "+15555550160", "liam.demo.patient@example.com", "13 Birch Lane, Greenview",
                "Emily Chen", "+15555550161", counter
        );
        Patient patientPriya = ensurePatient(
                "Priya", "Nair", LocalDate.of(1952, 1, 15), PatientGender.FEMALE, BloodGroup.O_POSITIVE,
                "+15555550170", "priya.demo.patient@example.com", "68 Garden Street, Rosewood",
                "Vikram Nair", "+15555550171", counter
        );
        Patient patientLucas = ensurePatient(
                "Lucas", "Reed", LocalDate.of(1988, 9, 9), PatientGender.MALE, BloodGroup.AB_NEGATIVE,
                "+15555550180", "lucas.demo.patient@example.com", "35 Pine Crescent, Eastbrook",
                "Amelia Reed", "+15555550181", counter
        );
        Patient patientZara = ensurePatient(
                "Zara", "Ali", LocalDate.of(2007, 5, 28), PatientGender.FEMALE, BloodGroup.B_POSITIVE,
                "+15555550190", "zara.demo.patient@example.com", "22 Meadow Way, Oakridge",
                "Farah Ali", "+15555550191", counter
        );

        Doctor doctorMaya = ensureDoctor(
                "Maya",
                "Patel",
                "Internal Medicine",
                "CLN-GEN-1001",
                "DEMO-LIC-GEN-001",
                "+15555550200",
                "maya.patel.demo@example.com",
                "General Medicine",
                primaryDoctorUser,
                counter
        );
        Doctor doctorOwen = ensureDoctor(
                "Owen",
                "Brooks",
                "Family Medicine",
                "CLN-FAM-1002",
                "DEMO-LIC-FAM-002",
                "+15555550210",
                "owen.brooks.demo@example.com",
                "Primary Care",
                secondaryDoctorUser,
                counter
        );
        Doctor doctorLeena = ensureDoctor(
                "Leena", "Shah", "Cardiology", "CLN-CAR-1003", null,
                "+15555550220", "leena.shah.demo@example.com", "Cardiology",
                tertiaryDoctorUser, counter
        );
        Doctor doctorDaniel = ensureDoctor(
                "Daniel", "Kim", "Pediatrics", "CLN-PED-1004", null,
                "+15555550230", "daniel.kim.demo@example.com", "Pediatrics",
                quaternaryDoctorUser, counter
        );

        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        Appointment activeAppointment = ensureAppointment(
                patientAsha,
                doctorMaya,
                now.plusDays(1).withHour(10).withMinute(0),
                "Headache assessment",
                AppointmentStatus.CONFIRMED,
                ACTIVE_CONSULTATION_MARKER
                        + " Patient reports mild tension-type headache for two days. Symptoms are improving with rest. No other documented symptoms.",
                counter
        );
        Appointment completedAppointment = ensureAppointment(
                patientAsha,
                doctorMaya,
                now.minusDays(7).withHour(11).withMinute(0),
                "Respiratory follow-up",
                AppointmentStatus.COMPLETED,
                COMPLETED_CONSULTATION_MARKER + " Review of resolving cough and throat irritation.",
                counter
        );
        ensureAppointment(
                patientNoah,
                doctorOwen,
                now.plusDays(2).withHour(14).withMinute(30),
                "Routine wellness review",
                AppointmentStatus.CANCELLED,
                CANCELLED_APPOINTMENT_MARKER + " Cancelled by patient due to a scheduling conflict.",
                counter
        );
        Appointment reminderAppointment = ensureAppointment(
                patientIra,
                doctorOwen,
                now.plusHours(3),
                "Nutrition and fatigue review",
                AppointmentStatus.SCHEDULED,
                REMINDER_APPOINTMENT_MARKER + " Bring recent laboratory reports if available.",
                counter
        );

        Appointment todayCheckIn = ensureAppointment(patientAsha, doctorMaya, now.plusMinutes(45),
                "Medication tolerance check", AppointmentStatus.SCHEDULED,
                "[DEMO:TODAY_CHECK_IN] Review symptom diary and current medicines.", counter);
        Appointment noahCompleted = ensureAppointment(patientNoah, doctorOwen, now.minusDays(14),
                "Blood pressure review", AppointmentStatus.COMPLETED,
                "[DEMO:NOAH_COMPLETED] Home readings reviewed during visit.", counter);
        Appointment meeraCompleted = ensureAppointment(patientMeera, doctorLeena, now.minusDays(30),
                "Cardiovascular risk review", AppointmentStatus.COMPLETED,
                "[DEMO:MEERA_COMPLETED] Discussed exercise tolerance and lipid results.", counter);
        Appointment ethanUpcoming = ensureAppointment(patientEthan, doctorDaniel, now.plusDays(5),
                "Seasonal allergy review", AppointmentStatus.CONFIRMED,
                "[DEMO:ETHAN_UPCOMING] Parent will bring current allergy list.", counter);
        Appointment sofiaUpcoming = ensureAppointment(patientSofia, doctorLeena, now.plusDays(7),
                "Palpitation follow-up", AppointmentStatus.SCHEDULED,
                "[DEMO:SOFIA_UPCOMING] Review symptom frequency and previous ECG report.", counter);
        Appointment liamCompleted = ensureAppointment(patientLiam, doctorMaya, now.minusDays(2),
                "Acute back strain review", AppointmentStatus.COMPLETED,
                "[DEMO:LIAM_COMPLETED] Symptoms improving with activity modification.", counter);
        Appointment priyaUpcoming = ensureAppointment(patientPriya, doctorOwen, now.plusDays(3),
                "Diabetes monitoring", AppointmentStatus.CONFIRMED,
                "[DEMO:PRIYA_UPCOMING] Bring glucose log and medication list.", counter);
        ensureAppointment(patientLucas, doctorMaya, now.minusDays(20), "Migraine review", AppointmentStatus.NO_SHOW,
                "[DEMO:LUCAS_NO_SHOW] Patient did not attend the scheduled visit.", counter);
        Appointment zaraUpcoming = ensureAppointment(patientZara, doctorDaniel, now.plusDays(1),
                "Sports physical", AppointmentStatus.SCHEDULED,
                "[DEMO:ZARA_UPCOMING] School sports clearance assessment.", counter);
        ensureAppointment(patientMeera, doctorLeena, now.plusDays(14), "Cardiology follow-up", AppointmentStatus.CONFIRMED,
                "[DEMO:MEERA_FUTURE] Review home blood pressure record.", counter);
        ensureAppointment(patientNoah, doctorOwen, now.plusDays(10), "Annual preventive visit", AppointmentStatus.SCHEDULED,
                "[DEMO:NOAH_FUTURE] Preventive screening review.", counter);
        ensureAppointment(patientSofia, doctorLeena, now.minusDays(1), "ECG review", AppointmentStatus.CANCELLED,
                "[DEMO:SOFIA_CANCELLED] Rescheduled at the patient's request.", counter);
        ensureAppointment(patientLiam, doctorMaya, now.plusDays(21), "Back pain follow-up", AppointmentStatus.SCHEDULED,
                "[DEMO:LIAM_FUTURE] Assess progress with home exercises.", counter);
        Appointment priyaCompleted = ensureAppointment(patientPriya, doctorOwen, now.minusDays(45),
                "Chronic care review", AppointmentStatus.COMPLETED,
                "[DEMO:PRIYA_COMPLETED] Reviewed glucose trend and daily activity.", counter);

        ensureConsultation(activeAppointment, false, "Headache", "Mild bilateral headache without documented red flags",
                "Comfortable at rest; no abnormal observation documented", null,
                "Rest, hydration, and symptom monitoring discussed", "Return if symptoms persist or worsen", counter);
        Consultation completedConsultation = ensureConsultation(completedAppointment, true, "Cough follow-up",
                "Cough and throat irritation are improving", "Afebrile and breathing comfortably",
                "Resolving upper respiratory symptoms", "Continue hydration and routine supportive care",
                "Follow up if symptoms recur", counter);
        Consultation noahConsultation = ensureConsultation(noahCompleted, true, "Blood pressure review",
                "No new symptoms documented", "Home readings reviewed; clinic reading stable",
                "Blood pressure monitoring visit", "Continue current documented care plan",
                "Repeat review in three months", counter);
        Consultation meeraConsultation = ensureConsultation(meeraCompleted, true, "Cardiovascular risk review",
                "No chest discomfort reported", "Exercise tolerance unchanged",
                "Risk-factor follow-up", "Lifestyle measures and monitoring reviewed",
                "Cardiology review in six months", counter);
        Consultation liamConsultation = ensureConsultation(liamCompleted, true, "Lower back discomfort",
                "Pain after lifting, now improving", "Movement mildly limited by discomfort",
                "Mechanical back strain documented", "Activity modification and gentle mobility discussed",
                "Review if pain does not continue to improve", counter);
        ensureConsultation(priyaCompleted, true, "Chronic care review", "No acute concerns documented",
                "Glucose log reviewed", "Routine diabetes monitoring visit",
                "Continue clinician-documented management plan", "Follow up with updated glucose log", counter);

        ensureMedicalRecord(
                patientAsha,
                doctorMaya,
                "Resolving upper respiratory symptoms",
                "Mild cough and throat irritation",
                MEDICAL_RECORD_PRIMARY_MARKER + " Symptoms were improving at the time of review; hydration and monitoring were discussed.",
                LocalDate.now(clock).minusDays(10),
                counter
        );
        ensureMedicalRecord(
                patientNoah,
                doctorOwen,
                "Elevated blood pressure under review",
                "No new symptoms documented",
                MEDICAL_RECORD_SECONDARY_MARKER + " Home readings were reviewed and follow-up monitoring was planned.",
                LocalDate.now(clock).minusDays(20),
                counter
        );

        ensureMedicalRecord(patientMeera, doctorLeena, "Cardiovascular risk-factor review",
                "No chest discomfort reported", "[DEMO:MEDICAL_RECORD_MEERA] Lipid results and exercise tolerance were reviewed.",
                LocalDate.now(clock).minusDays(31), counter);
        ensureMedicalRecord(patientLiam, doctorMaya, "Mechanical lower back strain",
                "Lower back discomfort after lifting", "[DEMO:MEDICAL_RECORD_LIAM] Symptoms were improving with gentle mobility.",
                LocalDate.now(clock).minusDays(3), counter);
        ensureMedicalRecord(patientPriya, doctorOwen, "Type 2 diabetes monitoring",
                "No acute symptoms documented", "[DEMO:MEDICAL_RECORD_PRIYA] Glucose trends and medication adherence were reviewed.",
                LocalDate.now(clock).minusDays(46), counter);
        ensureMedicalRecord(patientEthan, doctorDaniel, "Seasonal allergic rhinitis",
                "Sneezing and intermittent nasal congestion", "[DEMO:MEDICAL_RECORD_ETHAN] Trigger avoidance and symptom monitoring were discussed with parent.",
                LocalDate.now(clock).minusDays(60), counter);

        ensurePrescription(patientAsha, doctorMaya, LocalDate.now(clock).minusDays(2), PRESCRIPTION_MARKER,
                "Use only as documented and reviewed by the treating clinician.", List.of(
                        new PrescriptionItemSpec("Paracetamol", "500 mg", "Up to twice daily as needed", "3 days", "Take after food if needed for discomfort"),
                        new PrescriptionItemSpec("Saline nasal spray", "2 sprays per nostril", "Twice daily", "5 days", "Use as directed")
                ), counter);
        ensurePrescription(patientNoah, doctorOwen, LocalDate.now(clock).minusDays(14), "[DEMO:PRESCRIPTION_NOAH]",
                "Continue the documented regimen until the next review.", List.of(
                        new PrescriptionItemSpec("Amlodipine", "5 mg", "Once daily", "30 days", "Take at the same time each day")
                ), counter);
        ensurePrescription(patientMeera, doctorLeena, LocalDate.now(clock).minusDays(30), "[DEMO:PRESCRIPTION_MEERA]",
                "Medication plan reviewed during the cardiology visit.", List.of(
                        new PrescriptionItemSpec("Atorvastatin", "10 mg", "Once nightly", "30 days", "Take in the evening")
                ), counter);
        ensurePrescription(patientEthan, doctorDaniel, LocalDate.now(clock).minusDays(60), "[DEMO:PRESCRIPTION_ETHAN]",
                "Parent advised to follow the documented pediatric dosing instructions.", List.of(
                        new PrescriptionItemSpec("Cetirizine oral solution", "5 mg", "Once daily", "7 days", "Measure with the supplied dosing device")
                ), counter);

        requestAppointmentCommunication(activeAppointment, CommunicationType.APPOINTMENT_CONFIRMATION, counter);
        requestAppointmentCommunication(reminderAppointment, CommunicationType.APPOINTMENT_REMINDER, counter);
        requestAppointmentCommunication(completedAppointment, CommunicationType.CONSULTATION_COMPLETED, counter);
        requestAppointmentCommunication(todayCheckIn, CommunicationType.APPOINTMENT_CONFIRMATION, counter);
        requestAppointmentCommunication(ethanUpcoming, CommunicationType.APPOINTMENT_CONFIRMATION, counter);
        requestAppointmentCommunication(priyaUpcoming, CommunicationType.APPOINTMENT_CONFIRMATION, counter);
        requestAppointmentCommunication(zaraUpcoming, CommunicationType.APPOINTMENT_CONFIRMATION, counter);
        requestAppointmentCommunication(ethanUpcoming, CommunicationType.APPOINTMENT_REMINDER, counter);
        requestAppointmentCommunication(priyaUpcoming, CommunicationType.APPOINTMENT_REMINDER, counter);
        requestAppointmentCommunication(noahCompleted, CommunicationType.CONSULTATION_COMPLETED, counter);
        requestAppointmentCommunication(meeraCompleted, CommunicationType.CONSULTATION_COMPLETED, counter);
        requestAppointmentCommunication(liamCompleted, CommunicationType.CONSULTATION_COMPLETED, counter);
        requestAppointmentCommunication(priyaCompleted, CommunicationType.CONSULTATION_COMPLETED, counter);

        ensureFeedback(completedConsultation, 5, "Clear explanation and a helpful follow-up plan.", counter);
        ensureFeedback(noahConsultation, 4, "The visit was organized and the next steps were easy to understand.", counter);
        ensureFeedback(meeraConsultation, 5, "Thoughtful review and enough time to discuss my questions.", counter);
        ensureFeedback(liamConsultation, 4, "Practical guidance and a smooth clinic experience.", counter);

        return counter.toCounts();
    }

    private Map<RoleName, Role> roles() {
        Map<RoleName, Role> roles = new EnumMap<>(RoleName.class);
        for (RoleName roleName : RoleName.values()) {
            roles.put(roleName, roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Required role is not configured: " + roleName)));
        }
        return roles;
    }

    private String requiredDemoPassword() {
        if (properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException("DEMO_DATA_PASSWORD must be set when demo data is enabled");
        }

        return properties.password();
    }

    private AuthUser ensureUser(
            String username,
            String firstName,
            String lastName,
            Role role,
            String demoPassword,
            SeedCounter counter
    ) {
        String normalizedUsername = username.toLowerCase();
        String passwordHash = passwordEncoder.encode(demoPassword);
        return authUserRepository.findByUsernameIgnoreCase(normalizedUsername)
                .map(user -> {
                    user.replaceCredentials(passwordHash, firstName, lastName, true);
                    user.replaceRoles(Set.of(role));
                    return user;
                })
                .orElseGet(() -> {
                    AuthUser user = new AuthUser(normalizedUsername, passwordHash, firstName, lastName, true);
                    user.addRole(role);
                    counter.users++;
                    return authUserRepository.save(user);
                });
    }

    private Patient ensurePatient(
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            PatientGender gender,
            BloodGroup bloodGroup,
            String phone,
            String email,
            String address,
            String emergencyContactName,
            String emergencyContactPhone,
            SeedCounter counter
    ) {
        return patientRepository.findByEmailIgnoreCase(email)
                .map(patient -> {
                    patient.setFirstName(firstName);
                    patient.setLastName(lastName);
                    patient.setDateOfBirth(dateOfBirth);
                    patient.setGender(gender);
                    patient.setBloodGroup(bloodGroup);
                    patient.setPhone(phone);
                    patient.setEmail(email);
                    patient.setAddress(address);
                    patient.setEmergencyContactName(emergencyContactName);
                    patient.setEmergencyContactPhone(emergencyContactPhone);
                    return patient;
                })
                .orElseGet(() -> {
                    counter.patients++;
                    return patientRepository.save(new Patient(
                            firstName,
                            lastName,
                            dateOfBirth,
                            gender,
                            bloodGroup,
                            phone,
                            email,
                            address,
                            emergencyContactName,
                            emergencyContactPhone
                    ));
                });
    }

    private Doctor ensureDoctor(
            String firstName,
            String lastName,
            String specialization,
            String licenseNumber,
            String legacyLicenseNumber,
            String phone,
            String email,
            String department,
            AuthUser user,
            SeedCounter counter
    ) {
        Optional<Doctor> existingDoctor = doctorRepository.findByUserId(user.getId());
        if (existingDoctor.isEmpty() && legacyLicenseNumber != null) {
            existingDoctor = doctorRepository.findByLicenseNumber(legacyLicenseNumber);
        }
        if (existingDoctor.isEmpty()) {
            Optional<Doctor> licenseOwner = doctorRepository.findByLicenseNumber(licenseNumber);
            if (licenseOwner.isPresent()) {
                throw new IllegalStateException("Synthetic doctor license is already assigned to another record");
            }
        }
        return existingDoctor
                .map(doctor -> {
                    doctor.setFirstName(firstName);
                    doctor.setLastName(lastName);
                    doctor.setSpecialization(specialization);
                    doctor.setLicenseNumber(licenseNumber);
                    doctor.setPhone(phone);
                    doctor.setEmail(email);
                    doctor.setDepartment(department);
                    doctor.setUser(user);
                    return doctor;
                })
                .orElseGet(() -> {
                    counter.doctors++;
                    Doctor doctor = new Doctor(
                            firstName,
                            lastName,
                            specialization,
                            licenseNumber,
                            phone,
                            email,
                            department
                    );
                    doctor.setUser(user);
                    return doctorRepository.save(doctor);
                });
    }

    private Appointment ensureAppointment(
            Patient patient,
            Doctor doctor,
            LocalDateTime appointmentDateTime,
            String reason,
            AppointmentStatus status,
            String notes,
            SeedCounter counter
    ) {
        return appointmentRepository.findFirstByNotesContaining(marker(notes))
                .map(appointment -> {
                    appointment.setPatient(patient);
                    appointment.setDoctor(doctor);
                    appointment.setAppointmentDateTime(appointmentDateTime);
                    appointment.setReason(reason);
                    appointment.setStatus(status);
                    appointment.setNotes(notes);
                    return appointment;
                })
                .orElseGet(() -> {
                    counter.appointments++;
                    return appointmentRepository.save(new Appointment(
                            patient,
                            doctor,
                            appointmentDateTime,
                            reason,
                            status,
                            notes
                    ));
                });
    }

    private Consultation ensureConsultation(
            Appointment appointment,
            boolean completed,
            String chiefComplaint,
            String symptoms,
            String examination,
            String assessment,
            String treatment,
            String followUpInstructions,
            SeedCounter counter
    ) {
        return consultationRepository.findByAppointmentId(appointment.getId())
                .map(consultation -> {
                    updateConsultation(consultation, appointment, completed, chiefComplaint, symptoms, examination,
                            assessment, treatment, followUpInstructions);
                    return consultation;
                })
                .orElseGet(() -> {
                    Consultation consultation = new Consultation(appointment, consultationStartedAt(appointment));
                    updateConsultation(consultation, appointment, completed, chiefComplaint, symptoms, examination,
                            assessment, treatment, followUpInstructions);
                    counter.consultations++;
                    return consultationRepository.save(consultation);
                });
    }

    private void updateConsultation(
            Consultation consultation,
            Appointment appointment,
            boolean completed,
            String chiefComplaint,
            String symptoms,
            String examination,
            String assessment,
            String treatment,
            String followUpInstructions
    ) {
        consultation.updateClinicalNotes(
                chiefComplaint,
                symptoms,
                examination,
                assessment,
                treatment,
                followUpInstructions
        );
        if (completed) {
            consultation.complete(consultationStartedAt(appointment).plusSeconds(1800));
            appointment.setStatus(AppointmentStatus.COMPLETED);
        } else {
            consultation.reopen(Instant.now(clock).minusSeconds(900));
            appointment.setStatus(AppointmentStatus.CONFIRMED);
        }
    }

    private Instant consultationStartedAt(Appointment appointment) {
        return appointment.getAppointmentDateTime().atZone(clock.getZone()).toInstant();
    }

    private MedicalRecord ensureMedicalRecord(
            Patient patient,
            Doctor doctor,
            String diagnosis,
            String symptoms,
            String notes,
            LocalDate recordDate,
            SeedCounter counter
    ) {
        return medicalRecordRepository.findFirstByNotesContaining(marker(notes))
                .map(record -> {
                    record.update(patient, doctor, diagnosis, symptoms, notes, recordDate);
                    return record;
                })
                .orElseGet(() -> {
                    counter.medicalRecords++;
                    return medicalRecordRepository.save(new MedicalRecord(
                            patient,
                            doctor,
                            diagnosis,
                            symptoms,
                            notes,
                            recordDate
                    ));
                });
    }

    private Prescription ensurePrescription(
            Patient patient,
            Doctor doctor,
            LocalDate prescriptionDate,
            String marker,
            String notes,
            List<PrescriptionItemSpec> items,
            SeedCounter counter
    ) {
        String storedNotes = marker + " " + notes;
        List<PrescriptionItem> replacementItems = items.stream().map(PrescriptionItemSpec::toEntity).toList();
        return prescriptionRepository.findFirstByNotesContaining(marker)
                .map(prescription -> {
                    prescription.update(patient, doctor, prescriptionDate, storedNotes, replacementItems);
                    return prescription;
                })
                .orElseGet(() -> {
                    Prescription prescription = new Prescription(
                            patient,
                            doctor,
                            prescriptionDate,
                            storedNotes
                    );
                    replacementItems.forEach(prescription::addItem);
                    Prescription savedPrescription = prescriptionRepository.save(prescription);
                    counter.prescriptions++;
                    requestPrescriptionCommunication(savedPrescription, counter);
                    return savedPrescription;
                });
    }

    private void ensureFeedback(Consultation consultation, int rating, String comment, SeedCounter counter) {
        if (feedbackRepository.findByConsultationId(consultation.getId()).isPresent()) {
            return;
        }
        Appointment appointment = consultation.getAppointment();
        feedbackRepository.save(new Feedback(
                consultation,
                appointment.getPatient(),
                appointment.getDoctor(),
                rating,
                comment
        ));
        counter.feedback++;
    }

    private void requestAppointmentCommunication(
            Appointment appointment,
            CommunicationType type,
            SeedCounter counter
    ) {
        if (hasExistingAppointmentCommunication(appointment, type)) {
            return;
        }
        if (type == CommunicationType.APPOINTMENT_CONFIRMATION) {
            communicationService.createAppointmentConfirmation(appointment);
        } else if (type == CommunicationType.APPOINTMENT_REMINDER) {
            communicationService.createAppointmentReminder(appointment);
        } else {
            communicationService.createCommunication(new CommunicationCreateRequest(
                    appointment.getPatient().getId(),
                    appointment.getId(),
                    type,
                    CommunicationChannel.SMS,
                    appointment.getPatient().getPhone()
            ));
        }
        counter.communicationsRequested++;
    }

    private boolean hasExistingAppointmentCommunication(Appointment appointment, CommunicationType type) {
        return communicationRepository.existsByPatientIdAndAppointmentIdAndTypeAndChannelAndStatusIn(
                appointment.getPatient().getId(),
                appointment.getId(),
                type,
                CommunicationChannel.SMS,
                ACTIVE_COMMUNICATION_STATUSES
        );
    }

    private void requestPrescriptionCommunication(Prescription prescription, SeedCounter counter) {
        communicationService.createPrescriptionAvailable(prescription);
        counter.communicationsRequested++;
    }

    private String marker(String notes) {
        int markerEnd = notes.indexOf(']');
        if (markerEnd < 0) {
            return notes;
        }
        return notes.substring(0, markerEnd + 1);
    }

    private record PrescriptionItemSpec(
            String medicineName,
            String dosage,
            String frequency,
            String duration,
            String instructions
    ) {
        private PrescriptionItem toEntity() {
            return new PrescriptionItem(medicineName, dosage, frequency, duration, instructions);
        }
    }

    public record SeedCounts(
            int users,
            int patients,
            int doctors,
            int appointments,
            int consultations,
            int medicalRecords,
            int prescriptions,
            int communicationsRequested,
            int feedback
    ) {
        static SeedCounts empty() {
            return new SeedCounts(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static class SeedCounter {
        private int users;
        private int patients;
        private int doctors;
        private int appointments;
        private int consultations;
        private int medicalRecords;
        private int prescriptions;
        private int communicationsRequested;
        private int feedback;

        private SeedCounts toCounts() {
            return new SeedCounts(
                    users,
                    patients,
                    doctors,
                    appointments,
                    consultations,
                    medicalRecords,
                    prescriptions,
                    communicationsRequested,
                    feedback
            );
        }
    }
}
