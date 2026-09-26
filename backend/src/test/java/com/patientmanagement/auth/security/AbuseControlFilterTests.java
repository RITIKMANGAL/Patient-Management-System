package com.patientmanagement.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AbuseControlFilterTests {
    @Test
    void loginIsBoundedAndReturnsStandardJsonWithoutTrustingForwardedHeaders() throws Exception {
        var filter = new AbuseControlFilter(new SecurityErrorWriter(
                JsonMapper.builder().addModule(new JavaTimeModule()).build()),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), true);
        for (int i = 0; i < 21; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("192.0.2.10");
            request.addHeader("X-Forwarded-For", "192.0.2." + i);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> res.setContentType("passed"));
            if (i < 20) {
                assertThat(response.getContentType()).isEqualTo("passed");
            } else {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
                assertThat(response.getContentAsString()).contains("\"status\":429", "\"message\"");
            }
        }
        var other = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        other.setRemoteAddr("192.0.2.99");
        var response = new MockHttpServletResponse();
        filter.doFilter(other, response, (req, res) -> res.setContentType("passed"));
        assertThat(response.getContentType()).isEqualTo("passed");
    }
}
