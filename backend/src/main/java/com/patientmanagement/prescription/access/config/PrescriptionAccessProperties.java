package com.patientmanagement.prescription.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "prescription.access")
public record PrescriptionAccessProperties(long tokenExpirationHours) {

    public PrescriptionAccessProperties {
        if (tokenExpirationHours < 1) {
            throw new IllegalStateException("Prescription access token expiration must be at least 1 hour");
        }
    }
}
