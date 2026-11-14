package com.interval.apibackend.tracking.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.interval.apibackend.common.exception.BusinessRuleException;
import com.interval.apibackend.common.util.TimeUtils;

/**
 * Rentang tanggal LOKAL pengguna (inklusif di kedua ujung) -> rentang Instant [start, endExclusive).
 *
 * Default: berakhir hari ini dan mencakup 30 hari. Karena berakhir hari ini, occurrence mendatang tidak ikut
 * kecuali client mengirim "to". Batas atas memakai awal hari berikutnya (eksklusif), benar juga pada hari DST.
 */
public record LogDateRange(LocalDate from, LocalDate to, Instant start, Instant endExclusive) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 366;

    public static LogDateRange resolve(LocalDate from, LocalDate to, ZoneId zone, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate begin = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);

        if (end.isBefore(begin)) {
            throw new BusinessRuleException("Invalid date range", "to", "End date must not be earlier than start date");
        }
        if (ChronoUnit.DAYS.between(begin, end) + 1 > MAX_DAYS) {
            throw new BusinessRuleException("Invalid date range", "to",
                    "Date range must not exceed " + MAX_DAYS + " days");
        }
        return new LogDateRange(begin, end, TimeUtils.startOfDay(begin, zone), TimeUtils.startOfNextDay(end, zone));
    }
}
