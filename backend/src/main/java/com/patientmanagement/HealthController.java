package com.patientmanagement;

import com.patientmanagement.common.response.HealthResponse;
import com.patientmanagement.config.ApplicationProperties;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final ApplicationProperties applicationProperties;

    public HealthController(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", applicationProperties.name(), Instant.now());
    }
}
