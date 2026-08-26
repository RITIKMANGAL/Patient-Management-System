package com.patientmanagement.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.patientmanagement.auth.model.AuthUser;
import com.patientmanagement.auth.model.Role;
import com.patientmanagement.auth.model.RoleName;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final byte[] secret;

    @Autowired
    public JwtService(JwtProperties jwtProperties) {
        this(jwtProperties, Clock.systemUTC());
    }

    JwtService(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.secret = validateSecret(jwtProperties.secret());
    }

    public String createAccessToken(AuthUser user) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.accessTokenExpirationSeconds());
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .map(RoleName::name)
                .sorted()
                .toList();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            signedJwt.sign(new MACSigner(secret));
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to create access token", exception);
        }
    }

    public JwtPrincipal validateAccessToken(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!signedJwt.verify(new MACVerifier(secret))) {
                throw new BadCredentialsException("Invalid access token");
            }

            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime == null || expirationTime.toInstant().isBefore(Instant.now(clock))) {
                throw new BadCredentialsException("Invalid access token");
            }

            String username = claims.getStringClaim("username");
            if (username == null || username.isBlank()) {
                throw new BadCredentialsException("Invalid access token");
            }

            return new JwtPrincipal(
                    UUID.fromString(claims.getSubject()),
                    username,
                    extractRoles(claims.getStringListClaim("roles"))
            );
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid access token");
        }
    }

    public long accessTokenExpirationSeconds() {
        return jwtProperties.accessTokenExpirationSeconds();
    }

    private Set<RoleName> extractRoles(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BadCredentialsException("Invalid access token");
        }

        Set<RoleName> roles = new LinkedHashSet<>();
        for (String value : values) {
            roles.add(RoleName.valueOf(value));
        }
        return roles;
    }

    private byte[] validateSecret(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException("JWT secret must be configured");
        }

        byte[] bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        return bytes;
    }
}
