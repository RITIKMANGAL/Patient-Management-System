package com.patientmanagement.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.RefreshToken;
import com.patientmanagement.auth.repository.RefreshTokenRepository;
import com.patientmanagement.auth.security.JwtProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                new JwtProperties("test-only-jwt-secret-for-refresh-token-tests", 900, 604800),
                new SecureRandom(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void validateRefreshTokenReturnsActiveToken() {
        AuthUser user = enabledUser();
        RefreshToken refreshToken = new RefreshToken(user, refreshTokenService.hash("raw-token"), NOW.plusSeconds(60));
        when(refreshTokenRepository.findByTokenHashForUpdate(refreshTokenService.hash("raw-token")))
                .thenReturn(Optional.of(refreshToken));

        assertThat(refreshTokenService.validateRefreshToken("raw-token")).isSameAs(refreshToken);
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        AuthUser user = enabledUser();
        RefreshToken refreshToken = new RefreshToken(user, refreshTokenService.hash("raw-token"), NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(refreshTokenService.hash("raw-token")))
                .thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("raw-token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void refreshTokenExpiringNowIsRejected() {
        AuthUser user = enabledUser();
        RefreshToken refreshToken = new RefreshToken(user, refreshTokenService.hash("raw-token"), NOW);
        when(refreshTokenRepository.findByTokenHashForUpdate(refreshTokenService.hash("raw-token")))
                .thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("raw-token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void revokedRefreshTokenIsRejected() {
        AuthUser user = enabledUser();
        RefreshToken refreshToken = new RefreshToken(user, refreshTokenService.hash("raw-token"), NOW.plusSeconds(60));
        refreshToken.revoke();
        when(refreshTokenRepository.findByTokenHashForUpdate(refreshTokenService.hash("raw-token")))
                .thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("raw-token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void revokedTokenCannotBeReused() {
        AuthUser user = enabledUser();
        RefreshToken refreshToken = new RefreshToken(user, refreshTokenService.hash("raw-token"), NOW.plusSeconds(60));
        refreshToken.revoke();
        when(refreshTokenRepository.findByTokenHashForUpdate(refreshTokenService.hash("raw-token")))
                .thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("raw-token"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void hashDoesNotExposeRawToken() {
        String hash = refreshTokenService.hash("raw-token");

        assertThat(hash).hasSize(64);
        assertThat(hash).doesNotContain("raw-token");
    }

    @Test
    void logoutOnlyRevokesRefreshTokenOwnedByCurrentUser() {
        AuthUser tokenOwner = enabledUser();
        java.util.UUID tokenOwnerId = java.util.UUID.randomUUID();
        ReflectionTestUtils.setField(tokenOwner, "id", tokenOwnerId);
        RefreshToken refreshToken = new RefreshToken(
                tokenOwner,
                refreshTokenService.hash("raw-token"),
                NOW.plusSeconds(60)
        );
        when(refreshTokenRepository.findByTokenHash(refreshTokenService.hash("raw-token")))
                .thenReturn(Optional.of(refreshToken));

        refreshTokenService.revokeRefreshTokenForUser("raw-token", java.util.UUID.randomUUID());

        assertThat(refreshToken.isRevoked()).isFalse();

        refreshTokenService.revokeRefreshTokenForUser("raw-token", tokenOwnerId);

        assertThat(refreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(refreshToken);
    }

    private AuthUser enabledUser() {
        return new AuthUser(
                "refresh.user@example.com",
                "$2a$10$syntheticHashForRefreshTokenTests",
                "Refresh",
                "User",
                true
        );
    }
}
