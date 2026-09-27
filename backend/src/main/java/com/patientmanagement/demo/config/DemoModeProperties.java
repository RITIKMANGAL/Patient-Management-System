package com.patientmanagement.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.mode")
public record DemoModeProperties(boolean enabled, String adminUsername) {

    public DemoModeProperties {
        adminUsername = adminUsername == null || adminUsername.isBlank()
                ? "demo-admin@clinora.app"
                : adminUsername.trim();
    }
}
