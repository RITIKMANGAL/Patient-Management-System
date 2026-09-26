package com.patientmanagement.communication.config;

import com.patientmanagement.communication.model.CommunicationType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "communication.sms")
public record SmsProperties(
        boolean enabled,
        String provider,
        String sender,
        long timeoutSeconds,
        Templates templates
) {

    private static final String DEFAULT_PROVIDER = "noop";
    private static final String DEFAULT_SENDER = "PMCLINIC";

    public SmsProperties {
        provider = isBlank(provider) ? DEFAULT_PROVIDER : provider.trim().toLowerCase();
        sender = isBlank(sender) ? DEFAULT_SENDER : sender.trim();
        templates = templates == null ? new Templates(null, null, null, null, null) : templates;
        if (timeoutSeconds < 1) {
            throw new IllegalStateException("SMS provider timeout must be at least 1 second");
        }
    }

    public String templateIdFor(CommunicationType type) {
        return switch (type) {
            case APPOINTMENT_CONFIRMATION -> nullableTrim(templates.appointmentConfirmation());
            case APPOINTMENT_REMINDER -> nullableTrim(templates.appointmentReminder());
            case CONSULTATION_COMPLETED -> nullableTrim(templates.consultationCompleted());
            case PRESCRIPTION_AVAILABLE -> nullableTrim(templates.prescriptionAvailable());
            case FEEDBACK_REQUEST -> nullableTrim(templates.feedbackRequest());
        };
    }

    public record Templates(
            String appointmentConfirmation,
            String appointmentReminder,
            String consultationCompleted,
            String prescriptionAvailable,
            String feedbackRequest
    ) {
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullableTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
