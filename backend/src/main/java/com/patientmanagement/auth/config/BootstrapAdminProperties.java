package com.patientmanagement.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String username, String password, String firstName, String lastName) {
    @Override
    public String toString() {
        return "BootstrapAdminProperties[enabled=" + enabled + ", credentials=REDACTED]";
    }
}
