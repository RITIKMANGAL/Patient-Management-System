package com.patientmanagement.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.patientmanagement.auth.dto.*;
import com.patientmanagement.auth.model.RoleName;
import org.junit.jupiter.api.Test;

class SecretRedactionTests {
    @Test
    void diagnosticRepresentationsNeverIncludeCredentials() {
        String secret = "unique-sensitive-fixture";
        for (Object value : java.util.List.of(
                new LoginRequest("staff@example.invalid", secret),
                new RegisterRequest("staff@example.invalid", secret, "Staff", "User", RoleName.ADMIN, null),
                new RefreshTokenRequest(secret), new LogoutRequest(secret), new TokenResponse(secret, secret, "Bearer", 900),
                new JwtProperties(secret, 900, 3600),
                new com.patientmanagement.ai.config.AiProperties(true, "openai", "test", 15, secret, "https://example.com"),
                new com.patientmanagement.demo.config.DemoDataProperties(false, secret))) {
            assertThat(value.toString()).doesNotContain(secret).contains("redacted");
        }
    }
}
