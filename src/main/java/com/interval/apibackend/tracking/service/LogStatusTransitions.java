package com.interval.apibackend.tracking.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.interval.apibackend.tracking.entity.LogStatus;

/**
 * Aturan perubahan status yang boleh dilakukan CLIENT. Fungsi murni agar seluruh matriks mudah diuji.
 *
 *   PENDING   -> TAKEN, SKIPPED
 *   TAKEN     -> SKIPPED, PENDING (batalkan; hanya sebelum batas MISSED)
 *   SKIPPED   -> TAKEN,   PENDING (batalkan; hanya sebelum batas MISSED)
 *   MISSED    -> TAKEN, SKIPPED   (diminum/dilewati terlambat); tidak bisa kembali ke PENDING
 *   CANCELLED -> tidak bisa diubah
 *   status yang sama -> diizinkan (idempotent: retry setelah jaringan putus tidak error)
 *
 * MISSED dan CANCELLED hanya diatur backend, client tidak boleh mengirimnya.
 */
public final class LogStatusTransitions {

    public static final Set<LogStatus> CLIENT_SETTABLE = EnumSet.of(LogStatus.PENDING, LogStatus.TAKEN, LogStatus.SKIPPED);

    private LogStatusTransitions() {
    }

    /** Pesan pelanggaran, atau kosong jika perubahan diizinkan. */
    public static Optional<String> violation(LogStatus from, LogStatus to, Instant scheduledAt,
                                             Instant now, Duration missedAfter) {
        if (!CLIENT_SETTABLE.contains(to)) {
            return Optional.of("Status " + to + " cannot be set manually");
        }
        if (from == to) {
            return Optional.empty();
        }
        if (from == LogStatus.CANCELLED) {
            return Optional.of("Cannot change status from CANCELLED to " + to);
        }
        if (to == LogStatus.PENDING) {
            if (from == LogStatus.MISSED) {
                return Optional.of("Cannot change status from MISSED to PENDING");
            }
            // Sama dengan job: MISSED jika scheduledAt < now - missedAfter, yaitu now > scheduledAt + missedAfter.
            if (now.isAfter(scheduledAt.plus(missedAfter))) {
                return Optional.of("Cannot revert to PENDING after the missed threshold has passed");
            }
        }
        return Optional.empty();
    }
}
