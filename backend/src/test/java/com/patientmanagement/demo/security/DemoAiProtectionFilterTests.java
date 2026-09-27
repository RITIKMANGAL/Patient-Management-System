package com.patientmanagement.demo.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.auth.security.JwtPrincipal;
import com.patientmanagement.auth.security.SecurityErrorWriter;
import com.patientmanagement.demo.config.DemoAiProperties;
import com.patientmanagement.demo.config.DemoModeProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DemoAiProtectionFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limitsTheDemoAdministratorToConfiguredRequestsPerMinute() throws Exception {
        DemoAiProtectionFilter filter = filter(2);
        authenticate("demo-admin@clinora.app");

        for (int attempt = 0; attempt < 3; attempt++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(), response, (req, res) -> res.setContentType("passed"));
            if (attempt < 2) {
                assertThat(response.getContentType()).isEqualTo("passed");
            } else {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
            }
        }
    }

    @Test
    void rejectsOtherAuthenticatedUsersInDemoMode() throws Exception {
        DemoAiProtectionFilter filter = filter(2);
        authenticate("other-admin@example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(), response, (req, res) -> res.setContentType("passed"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AI demo access is restricted");
    }

    private DemoAiProtectionFilter filter(int requestsPerMinute) {
        return new DemoAiProtectionFilter(
                new DemoModeProperties(true, "demo-admin@clinora.app"),
                new DemoAiProperties(true, requestsPerMinute, 1, 1800, 1600),
                new SecurityErrorWriter(JsonMapper.builder().addModule(new JavaTimeModule()).build()),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private void authenticate(String username) {
        JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), username, Set.of(RoleName.ADMIN));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Set.of())
        );
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/api/v1/consultations/11111111-1111-1111-1111-111111111111/ai/draft");
    }
}
