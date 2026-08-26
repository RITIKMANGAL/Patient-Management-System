package com.patientmanagement.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.repository.DoctorRepository;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.patient.repository.PatientRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CoreSchemaIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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

        assertThat(constraintExists("doctors", "uk_doctors_license_number")).isTrue();
        assertThat(constraintExists("prescription_items", "fk_prescription_items_prescription")).isTrue();
        assertThat(constraintExists("users", "uk_users_username")).isTrue();
        assertThat(constraintExists("roles", "uk_roles_name")).isTrue();
        assertThat(constraintExists("refresh_tokens", "uk_refresh_tokens_token_hash")).isTrue();
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

        assertThat(patient.getId()).isNotNull();
        assertThat(patient.getCreatedAt()).isNotNull();
        assertThat(patient.getUpdatedAt()).isNotNull();
        assertThat(doctor.getId()).isNotNull();
        assertThat(doctor.getCreatedAt()).isNotNull();
        assertThat(doctor.getUpdatedAt()).isNotNull();
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

    private boolean roleExists(String roleName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE name = ?",
                Integer.class,
                roleName
        );
        return count != null && count == 1;
    }
}
