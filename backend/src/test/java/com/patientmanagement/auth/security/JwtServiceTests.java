package com.patientmanagement.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.Role;
import com.patientmanagement.auth.model.RoleName;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTests {

    private static final String TEST_SECRET =
            "test-only-jwt-secret-for-automated-tests-at-least-64-bytes-long";
    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                new JwtProperties(TEST_SECRET, 900, 604800),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void validHs256TokenReturnsPrincipal() {
        AuthUser user = user(RoleName.ADMIN);

        JwtPrincipal principal = jwtService.validateAccessToken(jwtService.createAccessToken(user));

        assertThat(principal.username()).isEqualTo(user.getUsername());
        assertThat(principal.roles()).containsExactly(RoleName.ADMIN);
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = jwtService.createAccessToken(user(RoleName.DOCTOR));
        String[] tokenParts = token.split("\\.");
        String signature = tokenParts[2];
        char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';
        String tamperedToken = tokenParts[0] + "." + tokenParts[1] + "." + replacement + signature.substring(1);

        assertThatThrownBy(() -> jwtService.validateAccessToken(tamperedToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token");
    }

    @Test
    void unexpectedSigningAlgorithmIsRejected() throws Exception {
        String token = signedToken(JWSAlgorithm.HS384, NOW.plusSeconds(900), UUID.randomUUID().toString());

        assertThatThrownBy(() -> jwtService.validateAccessToken(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token");
    }

    @Test
    void tokenExpiredAtCurrentInstantIsRejected() throws Exception {
        String token = signedToken(JWSAlgorithm.HS256, NOW, UUID.randomUUID().toString());

        assertThatThrownBy(() -> jwtService.validateAccessToken(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token");
    }

    @Test
    void missingSubjectIsRejected() throws Exception {
        String token = signedToken(JWSAlgorithm.HS256, NOW.plusSeconds(900), null);

        assertThatThrownBy(() -> jwtService.validateAccessToken(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token");
    }

    private String signedToken(JWSAlgorithm algorithm, Instant expiresAt, String subject) throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .claim("username", "jwt.user@example.com")
                .claim("roles", List.of(RoleName.ADMIN.name()))
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(expiresAt));
        if (subject != null) {
            claims.subject(subject);
        }

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(algorithm), claims.build());
        signedJwt.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
        return signedJwt.serialize();
    }

    private AuthUser user(RoleName roleName) {
        AuthUser user = new AuthUser(
                roleName.name().toLowerCase() + ".jwt@example.com",
                "$2a$10$syntheticHashForJwtServiceTests",
                "Jwt",
                "User",
                true
        );
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.addRole(new Role(roleName));
        return user;
    }
}
