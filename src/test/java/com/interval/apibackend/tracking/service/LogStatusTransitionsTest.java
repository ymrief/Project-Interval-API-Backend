package com.interval.apibackend.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.interval.apibackend.tracking.entity.LogStatus;

/** Matriks perubahan status. Tanpa Spring dan tanpa Docker. */
class LogStatusTransitionsTest {

    private static final Duration THRESHOLD = Duration.ofMinutes(60);
    private static final Instant SCHEDULED = Instant.parse("2026-11-12T13:00:00Z");
    private static final Instant BEFORE = SCHEDULED.minusSeconds(3600);
    private static final Instant AT_THRESHOLD = SCHEDULED.plus(THRESHOLD);

    private static Optional<String> check(LogStatus from, LogStatus to, Instant now) {
        return LogStatusTransitions.violation(from, to, SCHEDULED, now, THRESHOLD);
    }

    @Test
    void pending_canBeTakenOrSkipped() {
        assertThat(check(LogStatus.PENDING, LogStatus.TAKEN, BEFORE)).isEmpty();
        assertThat(check(LogStatus.PENDING, LogStatus.SKIPPED, BEFORE)).isEmpty();
    }

    @Test
    void takenAndSkipped_canSwitchBetweenEachOther() {
        assertThat(check(LogStatus.TAKEN, LogStatus.SKIPPED, BEFORE)).isEmpty();
        assertThat(check(LogStatus.SKIPPED, LogStatus.TAKEN, BEFORE)).isEmpty();
    }

    @Test
    void missed_canBeTakenOrSkippedLate_butNeverBackToPending() {
        Instant late = SCHEDULED.plus(Duration.ofHours(5));

        assertThat(check(LogStatus.MISSED, LogStatus.TAKEN, late)).isEmpty();
        assertThat(check(LogStatus.MISSED, LogStatus.SKIPPED, late)).isEmpty();
        assertThat(check(LogStatus.MISSED, LogStatus.PENDING, BEFORE))
                .contains("Cannot change status from MISSED to PENDING");
    }

    @Test
    void cancelled_isImmutable() {
        assertThat(check(LogStatus.CANCELLED, LogStatus.TAKEN, BEFORE))
                .contains("Cannot change status from CANCELLED to TAKEN");
        assertThat(check(LogStatus.CANCELLED, LogStatus.SKIPPED, BEFORE))
                .contains("Cannot change status from CANCELLED to SKIPPED");
        assertThat(check(LogStatus.CANCELLED, LogStatus.PENDING, BEFORE))
                .contains("Cannot change status from CANCELLED to PENDING");
    }

    @Test
    void clientCannotSetMissedOrCancelled_fromAnyStatus() {
        for (LogStatus from : LogStatus.values()) {
            assertThat(check(from, LogStatus.MISSED, BEFORE)).contains("Status MISSED cannot be set manually");
            assertThat(check(from, LogStatus.CANCELLED, BEFORE)).contains("Status CANCELLED cannot be set manually");
        }
    }

    @Test
    void revertToPending_isAllowedUpToAndIncludingTheThreshold() {
        // Sama dengan job MISSED: baru MISSED jika scheduledAt < now - threshold (ketat).
        assertThat(check(LogStatus.TAKEN, LogStatus.PENDING, BEFORE)).isEmpty();
        assertThat(check(LogStatus.TAKEN, LogStatus.PENDING, AT_THRESHOLD)).isEmpty();
        assertThat(check(LogStatus.SKIPPED, LogStatus.PENDING, AT_THRESHOLD)).isEmpty();
        assertThat(check(LogStatus.TAKEN, LogStatus.PENDING, AT_THRESHOLD.plusMillis(1)))
                .contains("Cannot revert to PENDING after the missed threshold has passed");
    }

    @Test
    void sameStatus_isIdempotent_evenPastTheThreshold() {
        Instant late = SCHEDULED.plus(Duration.ofHours(5));

        assertThat(check(LogStatus.TAKEN, LogStatus.TAKEN, late)).isEmpty();
        assertThat(check(LogStatus.SKIPPED, LogStatus.SKIPPED, late)).isEmpty();
        assertThat(check(LogStatus.PENDING, LogStatus.PENDING, late)).isEmpty();
    }
}
