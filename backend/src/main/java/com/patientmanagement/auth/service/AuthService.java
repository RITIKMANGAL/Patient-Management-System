package com.patientmanagement.auth.service;

import com.patientmanagement.auth.dto.LoginRequest;
import com.patientmanagement.auth.dto.RefreshTokenRequest;
import com.patientmanagement.auth.dto.RegisterRequest;
import com.patientmanagement.auth.dto.TokenResponse;
import com.patientmanagement.auth.dto.UserResponse;
import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.RefreshToken;
import com.patientmanagement.auth.model.Role;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.auth.repository.AuthUserRepository;
import com.patientmanagement.auth.repository.RoleRepository;
import com.patientmanagement.auth.security.CurrentUserService;
import com.patientmanagement.auth.security.JwtService;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.InvalidRequestException;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.doctor.repository.DoctorRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String AUTHENTICATION_FAILURE_MESSAGE = "Invalid username or password";

    private final AuthUserRepository authUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUserService currentUserService;
    private final DoctorRepository doctorRepository;

    public AuthService(
            AuthUserRepository authUserRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            CurrentUserService currentUserService,
            DoctorRepository doctorRepository
    ) {
        this.authUserRepository = authUserRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.currentUserService = currentUserService;
        this.doctorRepository = doctorRepository;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (authUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("Username already exists");
        }

        if (request.role() == null) {
            throw new InvalidRequestException("Staff role is required");
        }
        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new InvalidRequestException("Staff role is not configured"));
        Doctor doctor = null;
        if (request.role() == RoleName.DOCTOR) {
            if (request.doctorId() == null) {
                throw new InvalidRequestException("Doctor account requires an existing doctor record");
            }
            doctor = doctorRepository.findByIdForUpdate(request.doctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
            if (doctor.getUser() != null) {
                throw new DuplicateResourceException("Doctor already has a staff account");
            }
        } else if (request.doctorId() != null) {
            throw new InvalidRequestException("Only a doctor account can be linked to a doctor record");
        }

        AuthUser user = new AuthUser(
                username,
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                true
        );
        user.addRole(role);
        AuthUser savedUser = authUserRepository.save(user);
        if (doctor != null) {
            doctor.setUser(savedUser);
        }
        return toResponse(savedUser);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException(AUTHENTICATION_FAILURE_MESSAGE);
        }
        AuthUser user = authUserRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new BadCredentialsException(AUTHENTICATION_FAILURE_MESSAGE));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(AUTHENTICATION_FAILURE_MESSAGE);
        }

        return tokenResponse(user, refreshTokenService.createRefreshToken(user));
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        RefreshToken currentToken = refreshTokenService.validateRefreshToken(request.refreshToken());
        currentToken.revoke();

        AuthUser user = currentToken.getUser();
        String replacementRefreshToken = refreshTokenService.createRefreshToken(user);
        return tokenResponse(user, replacementRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        UUID userId = currentUserService.currentUserId()
                .orElseThrow(() -> new BadCredentialsException(AUTHENTICATION_FAILURE_MESSAGE));
        refreshTokenService.revokeRefreshTokenForUser(refreshToken, userId);
    }

    private TokenResponse tokenResponse(AuthUser user, String refreshToken) {
        return new TokenResponse(
                jwtService.createAccessToken(user),
                refreshToken,
                "Bearer",
                jwtService.accessTokenExpirationSeconds()
        );
    }

    private UserResponse toResponse(AuthUser user) {
        Set<RoleName> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
