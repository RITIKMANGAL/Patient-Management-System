package com.patientmanagement.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlywayMigrationTests {

    @Test
    void migrationsArePresentInExpectedOrder() throws Exception {
        List<String> migrations = migrationFileNames();

        assertThat(migrations).containsExactly(
                "V1__create_core_schema.sql",
                "V2__create_auth_schema.sql",
                "V3__create_appointment_schema.sql",
                "V4__create_communication_schema.sql",
                "V5__create_consultation_schema.sql",
                "V6__create_prescription_access_token_schema.sql",
                "V7__create_feedback_schema.sql",
                "V8__link_doctors_to_users.sql",
                "V9__explicit_communication_simulation_status.sql",
                "V10__prevent_overlapping_appointments.sql"
        );
    }

    private List<String> migrationFileNames() throws Exception {
        Path migrationDirectory = migrationDirectory();
        try (var files = Files.list(migrationDirectory)) {
            return files
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(".sql"))
                    .sorted(java.util.Comparator.comparingInt(name -> Integer.parseInt(name.substring(1, name.indexOf("__")))))
                    .toList();
        }
    }

    private Path migrationDirectory() throws URISyntaxException {
        var resource = getClass().getClassLoader().getResource("db/migration");
        assertThat(resource).isNotNull();
        return Path.of(resource.toURI());
    }
}
