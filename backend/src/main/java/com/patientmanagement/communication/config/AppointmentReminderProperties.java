package com.patientmanagement.communication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "communication.appointment-reminders")
public record AppointmentReminderProperties(
        boolean enabled,
        long lookAheadHours
) {

    public AppointmentReminderProperties {
        if (lookAheadHours < 1) {
            throw new IllegalStateException("Appointment reminder look-ahead must be at least 1 hour");
        }
    }
}
