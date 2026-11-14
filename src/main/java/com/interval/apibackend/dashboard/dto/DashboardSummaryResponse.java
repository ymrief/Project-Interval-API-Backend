package com.interval.apibackend.dashboard.dto;

import java.math.BigDecimal;

/**
 * todayScheduledCount = semua occurrence hari ini kecuali CANCELLED = taken + skipped + pending + missed.
 * weeklyAdherencePercentage = adherence 7 hari terakhir termasuk hari ini; null jika belum ada yang selesai.
 */
public record DashboardSummaryResponse(
        long activeMedicationCount,
        long todayScheduledCount,
        long todayTakenCount,
        long todaySkippedCount,
        long todayPendingCount,
        long todayMissedCount,
        BigDecimal weeklyAdherencePercentage
) {
}
