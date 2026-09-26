package com.patientmanagement.auth.service;

import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.RefreshToken;
import com.patientmanagement.auth.repository.RefreshTokenRepository;
import com.patientmanagement.auth.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 48;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this(refreshTokenRepository, jwtProperties, new SecureRandom(), Clock.systemUTC());
    }

    RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public String createRefreshToken(AuthUser user) {
        String rawToken = randomToken();
        RefreshToken refreshToken = new RefreshToken(
                user,
                hash(rawToken),
                Instant.now(clock).plusSeconds(jwtProperties.refreshTokenExpirationSeconds())
        );
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    public RefreshToken validateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (refreshToken.isRevoked()
                || !refreshToken.getExpiresAt().isAfter(Instant.now(clock))
                || !refreshToken.getUser().isEnabled()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        return refreshToken;
    }

    public void revokeRefreshTokenForUser(String rawToken, UUID userId) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(refreshToken -> refreshToken.getUser().getId().equals(userId))
                .filter(refreshToken -> !refreshToken.isRevoked())
                .ifPresent(refreshToken -> {
                    refreshToken.revoke();
                    refreshTokenRepository.save(refreshToken);
                });
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
