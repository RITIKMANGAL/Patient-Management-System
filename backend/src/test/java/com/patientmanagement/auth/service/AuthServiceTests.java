package com.patientmanagement.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.patientmanagement.doctor.repository.DoctorRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private DoctorRepository doctorRepository;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                authUserRepository,
                roleRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                currentUserService,
                doctorRepository
        );
    }

    @Test
    void successfulRegistrationCreatesReceptionistWithHashedPassword() {
        when(authUserRepository.existsByUsernameIgnoreCase("new.user@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.RECEPTIONIST)).thenReturn(Optional.of(new Role(RoleName.RECEPTIONIST)));
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            return user;
        });

        UserResponse response = authService.register(registerRequest());

        ArgumentCaptor<AuthUser> userCaptor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).save(userCaptor.capture());
        AuthUser savedUser = userCaptor.getValue();

        assertThat(response.username()).isEqualTo("new.user@example.com");
        assertThat(response.roles()).containsExactly(RoleName.RECEPTIONIST);
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("StrongPass123");
        assertThat(savedUser.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("StrongPass123", savedUser.getPasswordHash())).isTrue();
    }

    @Test
    void registrationCannotCreateAdmin() {
        when(authUserRepository.existsByUsernameIgnoreCase("new.user@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.RECEPTIONIST)).thenReturn(Optional.of(new Role(RoleName.RECEPTIONIST)));
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(registerRequest());

        assertThat(response.roles()).containsExactly(RoleName.RECEPTIONIST);
        assertThat(response.roles()).doesNotContain(RoleName.ADMIN);
    }

    @Test
    void registrationRejectsDuplicateUsername() {
        when(authUserRepository.existsByUsernameIgnoreCase("new.user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username already exists");
    }

    @Test
    void successfulLoginReturnsTokenResponse() {
        AuthUser user = enabledUser("login.user@example.com", "StrongPass123", RoleName.RECEPTIONIST);
        when(authUserRepository.findByUsernameIgnoreCase("login.user@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token");
        when(jwtService.createAccessToken(user)).thenReturn("access-token");
        when(jwtService.accessTokenExpirationSeconds()).thenReturn(900L);

        TokenResponse response = authService.login(new LoginRequest("login.user@example.com", "StrongPass123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    void invalidPasswordReturnsGenericFailure() {
        AuthUser user = enabledUser("login.user@example.com", "StrongPass123", RoleName.RECEPTIONIST);
        when(authUserRepository.findByUsernameIgnoreCase("login.user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("login.user@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void unknownUserReturnsGenericFailure() {
        when(authUserRepository.findByUsernameIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "StrongPass123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void disabledUserReturnsGenericFailure() {
        AuthUser user = disabledUser("login.user@example.com", "StrongPass123", RoleName.RECEPTIONIST);
        when(authUserRepository.findByUsernameIgnoreCase("login.user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("login.user@example.com", "StrongPass123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void successfulRefreshRotatesRefreshToken() {
        AuthUser user = enabledUser("login.user@example.com", "StrongPass123", RoleName.RECEPTIONIST);
        RefreshToken oldToken = new RefreshToken(user, "old-token-hash", java.time.Instant.now().plusSeconds(60));
        when(refreshTokenService.validateRefreshToken("old-refresh-token")).thenReturn(oldToken);
        when(refreshTokenService.createRefreshToken(user)).thenReturn("new-refresh-token");
        when(jwtService.createAccessToken(user)).thenReturn("new-access-token");
        when(jwtService.accessTokenExpirationSeconds()).thenReturn(900L);

        TokenResponse response = authService.refresh(new RefreshTokenRequest("old-refresh-token"));

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void logoutRevokesRefreshToken() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.currentUserId()).thenReturn(Optional.of(userId));

        authService.logout("refresh-token");

        verify(refreshTokenService).revokeRefreshTokenForUser("refresh-token", userId);
    }

    private RegisterRequest registerRequest() {
        return new RegisterRequest(
                "new.user@example.com",
                "StrongPass123",
                "New",
                "User",
                RoleName.RECEPTIONIST,
                null
        );
    }

    private AuthUser enabledUser(String username, String rawPassword, RoleName roleName) {
        AuthUser user = new AuthUser(username, passwordEncoder.encode(rawPassword), "Login", "User", true);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.addRole(new Role(roleName));
        return user;
    }

    private AuthUser disabledUser(String username, String rawPassword, RoleName roleName) {
        AuthUser user = new AuthUser(username, passwordEncoder.encode(rawPassword), "Login", "User", false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.addRole(new Role(roleName));
        return user;
    }
}
