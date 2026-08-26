package com.patientmanagement.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.patientmanagement.auth.dto.LoginRequest;
import com.patientmanagement.auth.dto.RefreshTokenRequest;
import com.patientmanagement.auth.dto.RegisterRequest;
import com.patientmanagement.auth.dto.TokenResponse;
import com.patientmanagement.auth.dto.UserResponse;
import com.patientmanagement.auth.model.RoleName;
import com.patientmanagement.auth.service.AuthService;
import com.patientmanagement.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AuthControllerTests {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = org.mockito.Mockito.mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();
    }

    @Test
    void registerReturnsUserWithoutPasswordFields() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(userResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.user@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registrationValidationReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void loginReturnsTokenResponseOnly() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new TokenResponse("access-token", "refresh-token", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void refreshReturnsRotatedTokenResponse() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenReturn(new TokenResponse("new-access-token", "new-refresh-token", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        doNothing().when(authService).logout("refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private String registerRequest() {
        return """
                {
                  "username": "new.user@example.com",
                  "password": "StrongPass123",
                  "firstName": "New",
                  "lastName": "User"
                }
                """;
    }

    private String loginRequest() {
        return """
                {
                  "username": "new.user@example.com",
                  "password": "StrongPass123"
                }
                """;
    }

    private UserResponse userResponse() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        return new UserResponse(
                UUID.randomUUID(),
                "new.user@example.com",
                "New",
                "User",
                true,
                Set.of(RoleName.RECEPTIONIST),
                now,
                now
        );
    }

    private LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
}
