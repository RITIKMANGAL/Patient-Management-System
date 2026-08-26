package com.patientmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI patientManagementOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Patient Management System API")
                        .version("v1")
                        .description("Core patient management APIs for patients, doctors, medical records, and prescriptions."))
                .addServersItem(new Server().url("/api/v1").description("Version 1 business API"));
    }
}
