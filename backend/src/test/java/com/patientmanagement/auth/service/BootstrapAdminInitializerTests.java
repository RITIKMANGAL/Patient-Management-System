package com.patientmanagement.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.patientmanagement.auth.config.BootstrapAdminProperties;
import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.Role;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.auth.repository.AuthUserRepository;
import com.patientmanagement.auth.repository.RoleRepository;
import jakarta.validation.Validation;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BootstrapAdminInitializerTests {
    @Test
    void createsOnlyFirstAdminWithHashAndNeverOverwritesAnExistingAdmin() {
        var users = mock(AuthUserRepository.class);
        var roles = mock(RoleRepository.class);
        var encoder = new BCryptPasswordEncoder();
        when(roles.findByName(RoleName.ADMIN)).thenReturn(Optional.of(new Role(RoleName.ADMIN)));
        when(users.existsByRolesName(RoleName.ADMIN)).thenReturn(false, true);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var initializer = new BootstrapAdminInitializer(new BootstrapAdminProperties(true,
                    "bootstrap@example.com", "test-only-strong-password", "Clinic", "Admin"), users, roles,
                    encoder, factory.getValidator(), mock(JdbcTemplate.class));
            initializer.run(null);
            initializer.run(null);
            var captured = ArgumentCaptor.forClass(AuthUser.class);
            verify(users, times(1)).save(captured.capture());
            assertThat(encoder.matches("test-only-strong-password", captured.getValue().getPasswordHash())).isTrue();
            assertThat(captured.getValue().getRoles()).extracting(Role::getName).containsExactly(RoleName.ADMIN);
        }
    }

    @Test
    void missingCredentialsCannotBootstrap() {
        var users = mock(AuthUserRepository.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var initializer = new BootstrapAdminInitializer(new BootstrapAdminProperties(true, "", "", "", ""),
                    users, mock(RoleRepository.class), new BCryptPasswordEncoder(), factory.getValidator(), mock(JdbcTemplate.class));
            assertThatThrownBy(() -> initializer.run(null)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Valid bootstrap administrator credentials must be configured");
            verify(users, never()).save(any());
        }
    }
}
