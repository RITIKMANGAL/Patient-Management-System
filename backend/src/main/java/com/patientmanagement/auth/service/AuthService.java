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
import com.patientmanagement.auth.security.JwtService;
import com.patientmanagement.common.exception.DuplicateResourceException;
import com.patientmanagement.common.exception.InvalidRequestException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    public AuthService(
            AuthUserRepository authUserRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.authUserRepository = authUserRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().toLowerCase();
        if (authUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("Username already exists");
        }

        Role receptionistRole = roleRepository.findByName(RoleName.RECEPTIONIST)
                .orElseThrow(() -> new InvalidRequestException("Default role is not configured"));

        AuthUser user = new AuthUser(
                username,
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                true
        );
        user.addRole(receptionistRole);
        return toResponse(authUserRepository.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
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
        refreshTokenService.revokeRefreshToken(refreshToken);
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
