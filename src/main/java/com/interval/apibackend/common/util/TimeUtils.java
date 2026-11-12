package com.interval.apibackend.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Database menyimpan UTC (Instant). API mengeluarkan waktu dengan offset
 * timezone pengguna. Semua konversi dilakukan lewat kelas ini.
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    /** Instant (UTC) -> OffsetDateTime dengan offset timezone pengguna, mis. 2026-11-11T08:00:00+07:00. */
    public static OffsetDateTime toUserTime(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toOffsetDateTime();
    }

    /** Awal hari lokal (inklusif) sebagai Instant. Pakai untuk batas bawah query: scheduledAt >= ... */
    public static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    /**
     * Awal hari lokal BERIKUTNYA (eksklusif). Pakai untuk batas atas: scheduledAt < ...
     * Lebih aman daripada "23:59:59.999" dan benar pada hari dengan perubahan DST.
     */
    public static Instant startOfNextDay(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    public static LocalDate today(ZoneId zone) {
        return LocalDate.now(zone);
    }
}
