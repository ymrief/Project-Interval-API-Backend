package com.interval.apibackend.tracking.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * totalScheduled = semua occurrence pada rentang KECUALI CANCELLED (taken + skipped + missed + pending).
 * adherencePercentage = taken / (taken + skipped + missed) * 100; PENDING dan CANCELLED tidak masuk penyebut.
 * Bernilai null jika penyebut 0.
 */
public record AdherenceResponse(
        long totalScheduled,
        long taken,
        long skipped,
        long missed,
        long pending,
        BigDecimal adherencePercentage
) {

    public static AdherenceResponse of(long taken, long skipped, long missed, long pending) {
        long resolved = taken + skipped + missed;
        return new AdherenceResponse(resolved + pending, taken, skipped, missed, pending,
                percentage(taken, resolved));
    }

    /** Dua desimal, pembulatan HALF_UP. null jika penyebut 0 (bukan 0, bukan error). Dipakai juga oleh Dashboard. */
    public static BigDecimal percentage(long taken, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(taken).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
