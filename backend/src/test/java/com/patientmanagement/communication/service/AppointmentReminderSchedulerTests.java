package com.patientmanagement.communication.service;

import static org.mockito.Mockito.verify;

import com.patientmanagement.communication.config.AppointmentReminderProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AppointmentReminderSchedulerTests {

    @Test
    void createDueAppointmentRemindersUsesConfiguredLookAheadWindow() {
        CommunicationService communicationService = org.mockito.Mockito.mock(CommunicationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneOffset.UTC);
        AppointmentReminderScheduler scheduler = new AppointmentReminderScheduler(
                communicationService,
                new AppointmentReminderProperties(true, 24),
                clock
        );

        scheduler.createDueAppointmentReminders();

        verify(communicationService).createDueAppointmentReminders(
                LocalDateTime.of(2026, 8, 31, 9, 0),
                LocalDateTime.of(2026, 9, 1, 9, 0)
        );
    }
}
