package com.interval.apibackend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class NotificationTimingTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @Test
    void dueWhenTheDoseIsAtMostNMinutesAway_inclusive() {
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.plus(Duration.ofMinutes(10)), 10)).isTrue();
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.plus(Duration.ofMinutes(5)), 10)).isTrue();
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.plusSeconds(1), 10)).isTrue();
    }

    @Test
    void notDueWhenTheDoseIsStillFurtherAwayThanN() {
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.plus(Duration.ofMinutes(10)).plusMillis(1), 10)).isFalse();
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.plus(Duration.ofHours(3)), 10)).isFalse();
    }

    @Test
    void notDueOnceTheDoseTimeHasArrivedOrPassed_theOnTimeReminderTakesOver() {
        assertThat(NotificationTiming.preReminderDue(NOW, NOW, 10)).isFalse();
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.minusSeconds(1), 10)).isFalse();
    }

    @Test
    void neverDueWhenPreRemindersAreOff() {
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.plusSeconds(60), 0)).isFalse();
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.plusSeconds(60), -5)).isFalse();
    }

    @Test
    void maximumLeadOf24HoursIsSupported() {
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.plus(Duration.ofHours(24)), 1440)).isTrue();
        assertThat(NotificationTiming.preReminderDue(NOW, NOW.plus(Duration.ofHours(24)).plusMillis(1), 1440)).isFalse();
    }
}
