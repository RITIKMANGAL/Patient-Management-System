package com.patientmanagement.communication.service;

import com.patientmanagement.communication.config.AppointmentReminderProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "communication.appointment-reminders",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AppointmentReminderScheduler {

    private final CommunicationService communicationService;
    private final AppointmentReminderProperties properties;
    private final Clock clock;

    public AppointmentReminderScheduler(
            CommunicationService communicationService,
            AppointmentReminderProperties properties
    ) {
        this(communicationService, properties, Clock.systemDefaultZone());
    }

    @Autowired
    AppointmentReminderScheduler(
            CommunicationService communicationService,
            AppointmentReminderProperties properties,
            Clock clock
    ) {
        this.communicationService = communicationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${communication.appointment-reminders.fixed-delay-ms:900000}",
            initialDelayString = "${communication.appointment-reminders.initial-delay-ms:60000}"
    )
    public void createDueAppointmentReminders() {
        LocalDateTime from = LocalDateTime.now(clock);
        communicationService.createDueAppointmentReminders(
                from,
                from.plusHours(properties.lookAheadHours())
        );
    }
}
