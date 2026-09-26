package com.patientmanagement.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.patientmanagement.appointment.model.Appointment;
import com.patientmanagement.appointment.model.AppointmentStatus;
import com.patientmanagement.appointment.repository.AppointmentRepository;
import com.patientmanagement.communication.model.Communication;
import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationStatus;
import com.patientmanagement.communication.model.CommunicationType;
import com.patientmanagement.communication.repository.CommunicationRepository;
import com.patientmanagement.consultation.model.Consultation;
import com.patientmanagement.consultation.model.ConsultationStatus;
import com.patientmanagement.consultation.repository.ConsultationRepository;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.repository.DoctorRepository;
import com.patientmanagement.feedback.model.Feedback;
import com.patientmanagement.feedback.model.FeedbackAccessToken;
import com.patientmanagement.feedback.repository.FeedbackAccessTokenRepository;
import com.patientmanagement.feedback.repository.FeedbackRepository;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.repository.PatientRepository;
import com.patientmanagement.prescription.access.model.PrescriptionAccessToken;
import com.patientmanagement.prescription.access.repository.PrescriptionAccessTokenRepository;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"communication.appointment-reminders.enabled=false", "demo.data.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
class CoreSchemaIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.secret", () -> "test-only-jwt-secret-for-testcontainers-integration-tests");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CommunicationRepository communicationRepository;

    @Autowired
    private ConsultationRepository consultationRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PrescriptionAccessTokenRepository prescriptionAccessTokenRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackAccessTokenRepository feedbackAccessTokenRepository;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;
    @Autowired
    private com.patientmanagement.auth.repository.AuthUserRepository users;
    @Autowired
    private com.patientmanagement.auth.repository.RoleRepository roles;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void realHttpStaffWorkflowEnforcesTwoDoctorIsolationAndTokenRotation() throws Exception {
        String password = "Synthetic-test-password-2026!";
        var admin = new com.patientmanagement.auth.model.AuthUser("synthetic.admin@example.invalid",
                passwords.encode(password), "Synthetic", "Admin", true);
        admin.addRole(roles.findByName(com.patientmanagement.auth.model.RoleName.ADMIN).orElseThrow());
        users.save(admin);
        var login = http("POST", "/api/v1/auth/login", null,
                java.util.Map.of("username", admin.getUsername(), "password", password), 200);
        String adminToken = login.path("accessToken").asText();
        http("POST", "/api/v1/auth/register", null, java.util.Map.of(), 401);
        var doctorIds = new java.util.ArrayList<String>();
        var doctorTokens = new java.util.ArrayList<String>();
        for (int i = 0; i < 2; i++) {
            var doctor = http("POST", "/api/v1/doctors", adminToken, java.util.Map.of(
                    "firstName", "Synthetic", "lastName", "Doctor", "specialization", "General Medicine",
                    "licenseNumber", "HTTP-DOCTOR-" + i, "phone", "+15555550100"), 201);
            String doctorId = doctor.path("id").asText();
            doctorIds.add(doctorId);
            String username = "synthetic.doctor" + i + "@example.invalid";
            http("POST", "/api/v1/auth/register", adminToken, java.util.Map.of(
                    "username", username, "password", password, "firstName", "Synthetic",
                    "lastName", "Doctor", "role", "DOCTOR", "doctorId", doctorId), 201);
            doctorTokens.add(http("POST", "/api/v1/auth/login", null,
                    java.util.Map.of("username", username, "password", password), 200).path("accessToken").asText());
        }
        http("POST", "/api/v1/auth/register", adminToken, java.util.Map.of(
                "username", "synthetic.reception@example.invalid", "password", password, "firstName", "Synthetic",
                "lastName", "Reception", "role", "RECEPTIONIST"), 201);
        String reception = http("POST", "/api/v1/auth/login", null, java.util.Map.of(
                "username", "synthetic.reception@example.invalid", "password", password), 200).path("accessToken").asText();
        http("POST", "/api/v1/auth/register", reception, java.util.Map.of("role", "ADMIN"), 403);
        http("POST", "/api/v1/auth/register", doctorTokens.getFirst(), java.util.Map.of("role", "ADMIN"), 403);
        var patientBody = new java.util.HashMap<String, Object>(java.util.Map.of(
                "firstName", "Synthetic", "lastName", "Patient", "dateOfBirth", "1990-01-01",
                "gender", "FEMALE", "phone", "+15555550100"));
        String patient = http("POST", "/api/v1/patients", reception, patientBody, 201).path("id").asText();
        patientBody.put("lastName", "Updated");
        http("PUT", "/api/v1/patients/" + patient, reception, patientBody, 200);
        http("GET", "/api/v1/patients/" + patient, null, null, 401);
        var appointmentBody = java.util.Map.of("patientId", patient, "doctorId", doctorIds.getFirst(),
                "appointmentDateTime", "2035-02-01T10:00:00", "reason", "Synthetic test visit", "status", "CONFIRMED");
        String appointment = http("POST", "/api/v1/appointments", reception, appointmentBody, 201).path("id").asText();
        http("POST", "/api/v1/appointments", reception, appointmentBody, 400);
        http("GET", "/api/v1/patients/" + patient, doctorTokens.getFirst(), null, 200);
        http("GET", "/api/v1/patients/" + patient, doctorTokens.get(1), null, 404);
        http("GET", "/api/v1/appointments/" + appointment, doctorTokens.get(1), null, 404);
        String consultation = http("POST", "/api/v1/appointments/" + appointment + "/consultation",
                doctorTokens.getFirst(), null, 201).path("id").asText();
        String record = http("POST", "/api/v1/medical-records", doctorTokens.getFirst(), java.util.Map.of(
                "patientId", patient, "doctorId", doctorIds.getFirst(), "diagnosis", "Synthetic observation",
                "recordDate", "2020-01-01"), 201).path("id").asText();
        String prescription = http("POST", "/api/v1/prescriptions", doctorTokens.getFirst(), java.util.Map.of(
                "patientId", patient, "doctorId", doctorIds.getFirst(), "prescriptionDate", "2020-01-01",
                "items", java.util.List.of(java.util.Map.of("medicineName", "Synthetic medicine", "dosage", "test",
                        "frequency", "test", "duration", "test"))), 201).path("id").asText();
        for (String path : java.util.List.of("/api/v1/consultations/" + consultation,
                "/api/v1/medical-records/" + record, "/api/v1/prescriptions/" + prescription)) {
            http("GET", path, doctorTokens.getFirst(), null, 200);
            http("GET", path, doctorTokens.get(1), null, 404);
            http("GET", path, reception, null, 403);
            http("GET", path, adminToken, null, 200);
        }
        http("PUT", "/api/v1/consultations/" + consultation, doctorTokens.getFirst(),
                java.util.Map.of("chiefComplaint", "Synthetic observation", "assessment", "Synthetic assessment"), 200);
        http("POST", "/api/v1/consultations/" + consultation + "/complete", doctorTokens.getFirst(), null, 200);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM communications WHERE patient_id=? AND status='SIMULATED'",
                Integer.class, java.util.UUID.fromString(patient))).isGreaterThanOrEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM communications WHERE patient_id=? AND sent_at IS NOT NULL",
                Integer.class, java.util.UUID.fromString(patient))).isZero();
        var rotated = http("POST", "/api/v1/auth/refresh", null,
                java.util.Map.of("refreshToken", login.path("refreshToken").asText()), 200);
        http("POST", "/api/v1/auth/refresh", null, java.util.Map.of("refreshToken", login.path("refreshToken").asText()), 401);
        http("POST", "/api/v1/auth/logout", rotated.path("accessToken").asText(),
                java.util.Map.of("refreshToken", rotated.path("refreshToken").asText()), 204);
        http("POST", "/api/v1/auth/refresh", null, java.util.Map.of("refreshToken", rotated.path("refreshToken").asText()), 401);
    }

    private com.fasterxml.jackson.databind.JsonNode http(String method, String path, String token, Object body, int status)
            throws Exception {
        var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                : java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = java.net.http.HttpClient.newHttpClient().send(builder.build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s", method, path).isEqualTo(status);
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }

    @Test
    void postCommitActionsNeverRunOnRollbackAndCannotRollBackSuccessfulClinicalWrite() {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        transaction.executeWithoutResult(status -> {
            com.patientmanagement.common.AfterCommitAction.run(() -> called.set(true));
            assertThat(called).isFalse();
            status.setRollbackOnly();
        });
        assertThat(called).isFalse();
        transaction.executeWithoutResult(status ->
                com.patientmanagement.common.AfterCommitAction.run(() -> {
                    called.set(true);
                    throw new IllegalStateException("Synthetic post-commit failure");
                }));
        assertThat(called).isTrue();
    }

    @Test
    void flywayCreatesCoreSchema() {
        assertThat(tableExists("patients")).isTrue();
        assertThat(tableExists("doctors")).isTrue();
        assertThat(tableExists("medical_records")).isTrue();
        assertThat(tableExists("prescriptions")).isTrue();
        assertThat(tableExists("prescription_items")).isTrue();
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("roles")).isTrue();
        assertThat(tableExists("user_roles")).isTrue();
        assertThat(tableExists("refresh_tokens")).isTrue();
        assertThat(tableExists("appointments")).isTrue();
        assertThat(tableExists("communications")).isTrue();
        assertThat(tableExists("consultations")).isTrue();
        assertThat(tableExists("prescription_access_tokens")).isTrue();
        assertThat(tableExists("feedback")).isTrue();
        assertThat(tableExists("feedback_access_tokens")).isTrue();

        assertThat(constraintExists("doctors", "uk_doctors_license_number")).isTrue();
        assertThat(constraintExists("prescription_items", "fk_prescription_items_prescription")).isTrue();
        assertThat(constraintExists("users", "uk_users_username")).isTrue();
        assertThat(constraintExists("roles", "uk_roles_name")).isTrue();
        assertThat(constraintExists("refresh_tokens", "uk_refresh_tokens_token_hash")).isTrue();
        assertThat(constraintExists("appointments", "fk_appointments_patient")).isTrue();
        assertThat(constraintExists("appointments", "fk_appointments_doctor")).isTrue();
        assertThat(constraintExists("appointments", "ck_appointments_status")).isTrue();
        assertThat(constraintExists("communications", "fk_communications_patient")).isTrue();
        assertThat(constraintExists("communications", "fk_communications_appointment")).isTrue();
        assertThat(constraintExists("communications", "ck_communications_type")).isTrue();
        assertThat(constraintExists("communications", "ck_communications_channel")).isTrue();
        assertThat(constraintExists("communications", "ck_communications_status")).isTrue();
        assertThat(constraintExists("consultations", "fk_consultations_appointment")).isTrue();
        assertThat(constraintExists("consultations", "uk_consultations_appointment")).isTrue();
        assertThat(constraintExists("consultations", "ck_consultations_status")).isTrue();
        assertThat(constraintExists("consultations", "ck_consultations_completed_at")).isTrue();
        assertThat(constraintExists("prescription_access_tokens", "uk_prescription_access_tokens_token_hash")).isTrue();
        assertThat(constraintExists("prescription_access_tokens", "fk_prescription_access_tokens_prescription")).isTrue();
        assertThat(indexExists("uk_prescription_access_tokens_active_prescription")).isTrue();
        assertThat(indexExists("idx_prescription_access_tokens_prescription_id")).isTrue();
        assertThat(indexExists("idx_prescription_access_tokens_token_hash")).isTrue();
        assertThat(indexExists("idx_prescription_access_tokens_expires_at")).isTrue();
        assertThat(indexExists("idx_prescription_access_tokens_revoked_at")).isTrue();
        assertThat(constraintExists("feedback", "uk_feedback_consultation")).isTrue();
        assertThat(constraintExists("feedback", "fk_feedback_consultation")).isTrue();
        assertThat(constraintExists("feedback", "fk_feedback_patient")).isTrue();
        assertThat(constraintExists("feedback", "fk_feedback_doctor")).isTrue();
        assertThat(constraintExists("feedback", "ck_feedback_rating")).isTrue();
        assertThat(constraintExists("feedback_access_tokens", "uk_feedback_access_tokens_token_hash")).isTrue();
        assertThat(constraintExists("feedback_access_tokens", "fk_feedback_access_tokens_consultation")).isTrue();
        assertThat(indexExists("uk_feedback_access_tokens_active_consultation")).isTrue();
        assertThat(indexExists("idx_feedback_consultation_id")).isTrue();
        assertThat(indexExists("idx_feedback_patient_id")).isTrue();
        assertThat(indexExists("idx_feedback_doctor_id")).isTrue();
        assertThat(indexExists("idx_feedback_rating")).isTrue();
        assertThat(indexExists("idx_feedback_created_at")).isTrue();
        assertThat(indexExists("idx_feedback_access_tokens_consultation_id")).isTrue();
        assertThat(indexExists("idx_feedback_access_tokens_token_hash")).isTrue();
        assertThat(indexExists("idx_feedback_access_tokens_expires_at")).isTrue();
        assertThat(indexExists("idx_feedback_access_tokens_revoked_at")).isTrue();
        assertThat(roleExists("ADMIN")).isTrue();
        assertThat(roleExists("DOCTOR")).isTrue();
        assertThat(roleExists("RECEPTIONIST")).isTrue();
    }

    @Test
    void uuidEntitiesPersistWithAuditing() {
        Patient patient = patientRepository.save(new Patient(
                "Asha",
                "Rao",
                LocalDate.of(1990, 1, 1),
                PatientGender.FEMALE,
                BloodGroup.O_POSITIVE,
                "+15555550100",
                "synthetic.integration.patient@example.com",
                "Synthetic address",
                "Synthetic Contact",
                "+15555550101"
        ));

        Doctor doctor = doctorRepository.save(new Doctor(
                "Kiran",
                "Shah",
                "Cardiology",
                "LIC-INTEGRATION-100",
                "+15555550200",
                "synthetic.integration.doctor@example.com",
                "Cardiology"
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                patient,
                doctor,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "Annual checkup",
                AppointmentStatus.SCHEDULED,
                "Bring reports"
        ));
        Communication communication = communicationRepository.save(new Communication(
                patient,
                appointment,
                CommunicationType.APPOINTMENT_CONFIRMATION,
                CommunicationChannel.SMS,
                "+15555550100"
        ));
        Consultation consultation = consultationRepository.save(new Consultation(
                appointment,
                java.time.Instant.parse("2026-09-01T10:00:00Z")
        ));
        Prescription prescription = prescriptionRepository.save(new Prescription(
                patient,
                doctor,
                LocalDate.of(2026, 9, 1),
                "Synthetic prescription"
        ));
        PrescriptionAccessToken accessToken = prescriptionAccessTokenRepository.save(new PrescriptionAccessToken(
                prescription,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                Instant.parse("2026-09-08T00:00:00Z")
        ));
        consultation.updateClinicalNotes("Chest pain", null, null, "Musculoskeletal pain", null, null);
        consultation.complete(Instant.parse("2026-09-01T10:30:00Z"));
        consultation = consultationRepository.save(consultation);
        Feedback feedback = feedbackRepository.save(new Feedback(
                consultation,
                patient,
                doctor,
                5,
                "Helpful visit"
        ));
        FeedbackAccessToken feedbackAccessToken = feedbackAccessTokenRepository.save(new FeedbackAccessToken(
                consultation,
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                Instant.parse("2026-09-08T00:00:00Z")
        ));

        assertThat(patient.getId()).isNotNull();
        assertThat(patient.getCreatedAt()).isNotNull();
        assertThat(patient.getUpdatedAt()).isNotNull();
        assertThat(doctor.getId()).isNotNull();
        assertThat(doctor.getCreatedAt()).isNotNull();
        assertThat(doctor.getUpdatedAt()).isNotNull();
        assertThat(appointment.getId()).isNotNull();
        assertThat(appointment.getCreatedAt()).isNotNull();
        assertThat(appointment.getUpdatedAt()).isNotNull();
        assertThat(communication.getId()).isNotNull();
        assertThat(communication.getCreatedAt()).isNotNull();
        assertThat(communication.getUpdatedAt()).isNotNull();
        assertThat(communication.getStatus()).isEqualTo(CommunicationStatus.PENDING);
        assertThat(consultation.getId()).isNotNull();
        assertThat(consultation.getCreatedAt()).isNotNull();
        assertThat(consultation.getUpdatedAt()).isNotNull();
        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.COMPLETED);
        assertThat(consultationRepository.findById(consultation.getId()).orElseThrow().getStatus())
                .isEqualTo(ConsultationStatus.COMPLETED);
        assertThat(prescription.getId()).isNotNull();
        assertThat(accessToken.getId()).isNotNull();
        assertThat(accessToken.getCreatedAt()).isNotNull();
        assertThat(accessToken.getRevokedAt()).isNull();
        assertThat(feedback.getId()).isNotNull();
        assertThat(feedback.getCreatedAt()).isNotNull();
        assertThat(feedback.getRating()).isEqualTo(5);
        assertThat(feedbackAccessToken.getId()).isNotNull();
        assertThat(feedbackAccessToken.getCreatedAt()).isNotNull();
        assertThat(feedbackAccessToken.getRevokedAt()).isNull();
    }

    @Test
    void appointmentSearchRunsWithoutOptionalFilters() {
        Patient patient = patientRepository.save(new Patient(
                "Leela",
                "Menon",
                LocalDate.of(1988, 5, 14),
                PatientGender.FEMALE,
                BloodGroup.A_POSITIVE,
                "+15555550300",
                "synthetic.integration.appointment.patient@example.com",
                "Synthetic address",
                "Synthetic Contact",
                "+15555550301"
        ));

        Doctor doctor = doctorRepository.save(new Doctor(
                "Arun",
                "Iyer",
                "General Medicine",
                "LIC-INTEGRATION-200",
                "+15555550400",
                "synthetic.integration.appointment.doctor@example.com",
                "General Medicine"
        ));

        appointmentRepository.save(new Appointment(
                patient,
                doctor,
                LocalDateTime.of(2026, 9, 2, 11, 0),
                "Follow-up",
                AppointmentStatus.CONFIRMED,
                null
        ));

        assertThat(appointmentRepository.findAll((root, query, criteriaBuilder) -> criteriaBuilder.conjunction(),
                PageRequest.of(0, 20)).getContent()).isNotEmpty();
    }

    @Test
    void doctorPatientSearchSupportsPatientSortingWithoutDuplicates() {
        Patient patient = patientRepository.save(new Patient(
                "Mina",
                "Kapoor",
                LocalDate.of(1992, 4, 18),
                PatientGender.FEMALE,
                BloodGroup.B_POSITIVE,
                "+15555550500",
                "synthetic.integration.scoped.patient@example.com",
                "Synthetic address",
                "Synthetic Contact",
                "+15555550501"
        ));
        Doctor doctor = doctorRepository.save(new Doctor(
                "Dev",
                "Malik",
                "General Medicine",
                "LIC-INTEGRATION-300",
                "+15555550600",
                "synthetic.integration.scoped.doctor@example.com",
                "General Medicine"
        ));

        appointmentRepository.save(new Appointment(
                patient,
                doctor,
                LocalDateTime.of(2026, 9, 3, 9, 0),
                "Initial visit",
                AppointmentStatus.COMPLETED,
                null
        ));
        appointmentRepository.save(new Appointment(
                patient,
                doctor,
                LocalDateTime.of(2026, 9, 10, 9, 0),
                "Follow-up visit",
                AppointmentStatus.SCHEDULED,
                null
        ));

        var patients = patientRepository.findPatientsForDoctor(
                doctor.getId(),
                PageRequest.of(0, 20, Sort.by("createdAt"))
        );

        assertThat(patients.getContent()).extracting(Patient::getId).containsExactly(patient.getId());
        assertThat(patients.getTotalElements()).isEqualTo(1);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        """,
                Integer.class,
                tableName
        );
        return count != null && count == 1;
    }

    @Test
    void simultaneousOverlappingBookingsHaveOnlyOneWinner() throws Exception {
        var patient = patientRepository.save(new Patient("Synthetic", "Concurrency", LocalDate.of(1990, 1, 1),
                PatientGender.FEMALE, BloodGroup.O_POSITIVE, "+15555550100", null, null, null, null));
        var doctor = doctorRepository.save(new Doctor("Synthetic", "Concurrency", "General Medicine",
                "CONCURRENT-" + java.util.UUID.randomUUID(), "+15555550101", null, null));
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> booking = () -> {
                try (var connection = java.sql.DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                    connection.setAutoCommit(false);
                    ready.countDown();
                    assertThat(start.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    try {
                        insertBooking(connection, patient.getId(), doctor.getId(), "2035-01-01T10:00:00");
                        connection.commit();
                        return "committed";
                    } catch (java.sql.SQLException exception) {
                        connection.rollback();
                        return exception.getSQLState();
                    }
                }
            };
            var first = executor.submit(booking);
            var second = executor.submit(booking);
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results.stream().filter("committed"::equals).count()).isEqualTo(1);
            // PostgreSQL can abort a simultaneous exclusion-index insertion as a deadlock victim.
            assertThat(results.stream().filter(result -> !result.equals("committed")).toList())
                    .hasSize(1).allMatch(result -> result.equals("23P01") || result.equals("40P01"));
        }
        try (var connection = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            insertBooking(connection, patient.getId(), doctor.getId(), "2035-01-01T10:30:00");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appointments WHERE doctor_id = ?",
                Integer.class, doctor.getId())).isEqualTo(2);
    }

    private void insertBooking(java.sql.Connection connection, java.util.UUID patient, java.util.UUID doctor,
                               String time) throws java.sql.SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO appointments (id, patient_id, doctor_id, appointment_date_time, reason, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Synthetic concurrency test', 'SCHEDULED', now(), now())
                """)) {
            statement.setObject(1, java.util.UUID.randomUUID());
            statement.setObject(2, patient);
            statement.setObject(3, doctor);
            statement.setObject(4, LocalDateTime.parse(time));
            statement.executeUpdate();
        }
    }

    private boolean constraintExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE table_schema = 'public'
                          AND table_name = ?
                          AND constraint_name = ?
                        """,
                Integer.class,
                tableName,
                constraintName
        );
        return count != null && count == 1;
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = ?
                        """,
                Integer.class,
                indexName
        );
        return count != null && count == 1;
    }

    private boolean roleExists(String roleName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE name = ?",
                Integer.class,
                roleName
        );
        return count != null && count == 1;
    }
}
