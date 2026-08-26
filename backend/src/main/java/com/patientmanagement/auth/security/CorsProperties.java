package com.patientmanagement.auth.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
