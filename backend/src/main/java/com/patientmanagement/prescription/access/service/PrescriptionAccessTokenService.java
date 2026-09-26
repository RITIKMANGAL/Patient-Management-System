package com.patientmanagement.prescription.access.service;

import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.prescription.access.config.PrescriptionAccessProperties;
import com.patientmanagement.prescription.access.dto.PrescriptionAccessTokenResponse;
import com.patientmanagement.prescription.access.dto.PrescriptionAccessStatusResponse;
import com.patientmanagement.prescription.access.model.PrescriptionAccessToken;
import com.patientmanagement.prescription.access.repository.PrescriptionAccessTokenRepository;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import com.patientmanagement.prescription.service.PrescriptionPdfService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionAccessTokenService {

    private static final int TOKEN_BYTES = 48;

    private final PrescriptionAccessTokenRepository accessTokenRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionPdfService prescriptionPdfService;
    private final PrescriptionAccessProperties properties;
    private final ClinicalAccessService clinicalAccessService;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public PrescriptionAccessTokenService(
            PrescriptionAccessTokenRepository accessTokenRepository,
            PrescriptionRepository prescriptionRepository,
            PrescriptionPdfService prescriptionPdfService,
            PrescriptionAccessProperties properties,
            ClinicalAccessService clinicalAccessService
    ) {
        this(
                accessTokenRepository,
                prescriptionRepository,
                prescriptionPdfService,
                properties,
                clinicalAccessService,
                new SecureRandom(),
                Clock.systemUTC()
        );
    }

    PrescriptionAccessTokenService(
            PrescriptionAccessTokenRepository accessTokenRepository,
            PrescriptionRepository prescriptionRepository,
            PrescriptionPdfService prescriptionPdfService,
            PrescriptionAccessProperties properties,
            ClinicalAccessService clinicalAccessService,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.accessTokenRepository = accessTokenRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionPdfService = prescriptionPdfService;
        this.properties = properties;
        this.clinicalAccessService = clinicalAccessService;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    PrescriptionAccessTokenService(
            PrescriptionAccessTokenRepository accessTokenRepository,
            PrescriptionRepository prescriptionRepository,
            PrescriptionPdfService prescriptionPdfService,
            PrescriptionAccessProperties properties,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this(accessTokenRepository, prescriptionRepository, prescriptionPdfService, properties, null, secureRandom, clock);
    }

    @Transactional
    public PrescriptionAccessTokenResponse createAccessToken(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
        requirePrescriptionAccess(prescription);
        Instant now = Instant.now(clock);

        revokeUnrevokedTokens(prescriptionId, now);

        String rawToken = randomToken();
        Instant expiresAt = now.plusSeconds(properties.tokenExpirationHours() * 3600);
        accessTokenRepository.save(new PrescriptionAccessToken(prescription, hash(rawToken), expiresAt));

        return new PrescriptionAccessTokenResponse(rawToken, expiresAt);
    }

    @Transactional(readOnly = true)
    public PrescriptionAccessStatusResponse getActiveAccessStatus(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
        requirePrescriptionAccess(prescription);
        Instant now = Instant.now(clock);
        return accessTokenRepository.findByPrescriptionIdAndRevokedAtIsNull(prescriptionId).stream()
                .filter(token -> token.getExpiresAt().isAfter(now))
                .findFirst()
                .map(token -> new PrescriptionAccessStatusResponse(true, token.getExpiresAt()))
                .orElseGet(() -> new PrescriptionAccessStatusResponse(false, null));
    }

    @Transactional
    public void revokeActiveAccessToken(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
        requirePrescriptionAccess(prescription);
        revokeUnrevokedTokens(prescriptionId, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public byte[] generatePrescriptionPdf(String rawToken) {
        PrescriptionAccessToken accessToken = validate(rawToken);
        return prescriptionPdfService.generatePrescriptionPdf(accessToken.getPrescription().getId());
    }

    String hash(String rawToken) {
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

    private PrescriptionAccessToken validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new PrescriptionAccessDeniedException();
        }

        PrescriptionAccessToken accessToken = accessTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(PrescriptionAccessDeniedException::new);

        Instant now = Instant.now(clock);
        if (accessToken.getRevokedAt() != null || !accessToken.getExpiresAt().isAfter(now)) {
            throw new PrescriptionAccessDeniedException();
        }

        return accessToken;
    }

    private void revokeUnrevokedTokens(UUID prescriptionId, Instant revokedAt) {
        accessTokenRepository.findByPrescriptionIdAndRevokedAtIsNull(prescriptionId)
                .forEach(accessToken -> accessToken.revoke(revokedAt));
    }

    private void requirePrescriptionAccess(Prescription prescription) {
        if (clinicalAccessService != null) {
            clinicalAccessService.requirePrescriptionAccess(prescription);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
