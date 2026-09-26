package com.patientmanagement.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.data")
public record DemoDataProperties(boolean enabled, String password) {
    @Override
    public String toString() {
        return "DemoDataProperties[enabled=" + enabled + ", password=redacted]";
    }
}
