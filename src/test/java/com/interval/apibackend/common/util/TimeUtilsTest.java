package com.interval.apibackend.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeUtilsTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");

    @Test
    void format_usesUserOffset_andAlwaysShowsSeconds() {
        assertThat(TimeUtils.format(Instant.parse("2026-11-11T01:00:00Z"), JAKARTA))
                .isEqualTo("2026-11-11T08:00:00+07:00");
        assertThat(TimeUtils.format(Instant.parse("2026-01-15T12:00:00Z"), ZoneId.of("America/New_York")))
                .isEqualTo("2026-01-15T07:00:00-05:00");
    }

    @Test
    void format_keepsMilliseconds() {
        assertThat(TimeUtils.format(Instant.parse("2026-11-11T01:00:00.123Z"), JAKARTA))
                .isEqualTo("2026-11-11T08:00:00.123+07:00");
    }

    @Test
    void format_nullInstant_returnsNull() {
        assertThat(TimeUtils.format(null, JAKARTA)).isNull();
    }

    @Test
    void dayBoundaries_areLocalMidnightsConvertedToUtc() {
        LocalDate day = LocalDate.of(2026, 11, 11);

        assertThat(TimeUtils.startOfDay(day, JAKARTA)).isEqualTo(Instant.parse("2026-11-10T17:00:00Z"));
        assertThat(TimeUtils.startOfNextDay(day, JAKARTA)).isEqualTo(Instant.parse("2026-11-11T17:00:00Z"));
    }

    @Test
    void dayBoundaries_handleDaylightSavingDayLength() {
        // 8 Maret 2026: hari DST di New York hanya 23 jam.
        LocalDate dstDay = LocalDate.of(2026, 3, 8);
        ZoneId newYork = ZoneId.of("America/New_York");

        long hours = java.time.Duration.between(
                TimeUtils.startOfDay(dstDay, newYork), TimeUtils.startOfNextDay(dstDay, newYork)).toHours();

        assertThat(hours).isEqualTo(23);
    }
}
