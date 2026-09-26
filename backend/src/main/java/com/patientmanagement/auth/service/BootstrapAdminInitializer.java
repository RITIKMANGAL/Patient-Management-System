package com.patientmanagement.auth.service;

import com.patientmanagement.auth.config.BootstrapAdminProperties;
import com.patientmanagement.auth.dto.RegisterRequest;
import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.auth.repository.AuthUserRepository;
import com.patientmanagement.auth.repository.RoleRepository;
import jakarta.validation.Validator;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "security.bootstrap-admin", name = "enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final BootstrapAdminProperties properties;
    private final AuthUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final JdbcTemplate jdbcTemplate;

    public BootstrapAdminInitializer(BootstrapAdminProperties properties, AuthUserRepository users,
            RoleRepository roles, PasswordEncoder passwordEncoder, Validator validator, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        // Serialize first-admin creation across application instances using the same database.
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(731902001)");
        if (users.existsByRolesName(RoleName.ADMIN)) {
            return;
        }
        RegisterRequest request = new RegisterRequest(properties.username(), properties.password(),
                properties.firstName(), properties.lastName(), RoleName.ADMIN, null);
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalStateException("Valid bootstrap administrator credentials must be configured");
        }
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("Bootstrap administrator username is already in use");
        }
        AuthUser user = new AuthUser(username, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim(), true);
        user.addRole(roles.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Administrator role is not configured")));
        users.save(user);
    }
}
