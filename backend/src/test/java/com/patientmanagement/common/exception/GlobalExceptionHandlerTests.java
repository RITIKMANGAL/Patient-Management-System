package com.patientmanagement.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTests {

    private MockMvc mockMvc;

    @Test
    void concurrentDatabaseWriteUsesSanitizedConflict() {
        var response = new GlobalExceptionHandler().handleConcurrentWrite();
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(response.getBody().message())
                .isEqualTo("Request conflicts with a concurrent change. Please retry.");
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator())
                .build();
    }

    @Test
    void validationErrorUsesErrorResponse() throws Exception {
        mockMvc.perform(post("/test/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
    }

    @Test
    void malformedUuidUsesSanitizedErrorResponse() throws Exception {
        mockMvc.perform(get("/test/resources/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'id'"));
    }

    @Test
    void missingStaticResourceUsesSanitizedNotFoundResponse() {
        var response = new GlobalExceptionHandler().handleMissingResource();
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(404);
        org.assertj.core.api.Assertions.assertThat(response.getBody().message()).isEqualTo("Resource not found");
    }

    @Test
    void missingRequestParameterUsesSanitizedErrorResponse() throws Exception {
        mockMvc.perform(get("/test/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'value'"));
    }

    @Test
    void malformedRequestBodyUsesSanitizedErrorResponse() throws Exception {
        mockMvc.perform(post("/test/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void unsupportedMethodUsesErrorResponse() throws Exception {
        mockMvc.perform(patch("/test/resources/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Synthetic\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("HTTP method is not supported for this endpoint"));
    }

    @Test
    void unsupportedMediaTypeUsesErrorResponse() throws Exception {
        mockMvc.perform(post("/test/resources")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Synthetic"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Media type is not supported"));
    }

    @Test
    void resourceNotFoundUsesErrorResponse() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Synthetic resource not found"));
    }

    @Test
    void duplicateResourceUsesErrorResponse() throws Exception {
        mockMvc.perform(post("/test/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Synthetic resource already exists"));
    }

    @Test
    void invalidBusinessOperationUsesErrorResponse() throws Exception {
        mockMvc.perform(post("/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Synthetic business rule failed"));
    }

    private LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    @RestController
    private static class TestController {

        @GetMapping("/test/resources/{id}")
        String getResource(@PathVariable UUID id) {
            return id.toString();
        }

        @PostMapping(value = "/test/resources", consumes = MediaType.APPLICATION_JSON_VALUE)
        String createResource(@Valid @RequestBody TestRequest request) {
            return request.name();
        }

        @GetMapping("/test/required-param")
        String requiredParameter(@RequestParam String value) {
            return value;
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new ResourceNotFoundException("Synthetic resource not found");
        }

        @PostMapping("/test/duplicate")
        String duplicate() {
            throw new DuplicateResourceException("Synthetic resource already exists");
        }

        @PostMapping("/test/invalid")
        String invalid() {
            throw new InvalidRequestException("Synthetic business rule failed");
        }
    }

    private record TestRequest(@NotBlank String name) {
    }
}
