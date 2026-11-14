package com.interval.apibackend.tracking.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.interval.apibackend.schedule.entity.FrequencyType;

/**
 * Menghitung kapan saja sebuah jadwal jatuh dalam jendela waktu [from, until). FUNGSI MURNI: tanpa database,
 * tanpa jam sistem, sehingga semua kasus sulit (pergantian hari, timezone, DST) bisa diuji dengan tepat.
 *
 * Aturan:
 *  - Jam dibaca sebagai waktu dinding di timezone pengguna, lalu dikonversi ke Instant (UTC).
 *  - DAILY   : setiap hari dari startDate.
 *  - WEEKLY  : hanya pada hari yang sama dengan dayOfWeek tiap jam (ISO: Senin=1 ... Minggu=7).
 *  - INTERVAL: startDate + k * intervalDays (k >= 0); startDate adalah acuan tetap, BUKAN "hari ini".
 *  - startDate dan endDate keduanya inklusif (tanggal lokal).
 *  - DST: jam yang tidak ada (gap) digeser maju; jam ganda (overlap) memakai offset yang lebih awal
 *    (perilaku ZonedDateTime.of).
 */
public final class OccurrenceCalculator {

    public record TimeSlot(UUID scheduleTimeId, LocalTime time, Integer dayOfWeek) {
    }

    public record Occurrence(UUID scheduleTimeId, Instant scheduledAt) {
    }

    private OccurrenceCalculator() {
    }

    public static List<Occurrence> compute(FrequencyType type, Integer intervalDays, LocalDate startDate,
                                           LocalDate endDate, List<TimeSlot> slots, ZoneId zone,
                                           Instant from, Instant until) {
        if (type == null || startDate == null || slots == null || slots.isEmpty() || !from.isBefore(until)) {
            return List.of();
        }
        if (type == FrequencyType.INTERVAL && (intervalDays == null || intervalDays < 1)) {
            return List.of();
        }

        // Rentang tanggal lokal kandidat: jendela +/- 1 hari (offset timezone maksimal 14 jam),
        // dipotong oleh startDate dan endDate. Hasil akhir tetap disaring ketat berdasarkan Instant.
        LocalDate first = from.atZone(zone).toLocalDate().minusDays(1);
        if (first.isBefore(startDate)) {
            first = startDate;
        }
        LocalDate last = until.atZone(zone).toLocalDate().plusDays(1);
        if (endDate != null && endDate.isBefore(last)) {
            last = endDate;
        }

        List<Occurrence> result = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (type == FrequencyType.INTERVAL && ChronoUnit.DAYS.between(startDate, date) % intervalDays != 0) {
                continue;
            }
            for (TimeSlot slot : slots) {
                if (type == FrequencyType.WEEKLY
                        && (slot.dayOfWeek() == null || slot.dayOfWeek() != date.getDayOfWeek().getValue())) {
                    continue;
                }
                Instant at = ZonedDateTime.of(date, slot.time(), zone).toInstant();
                if (!at.isBefore(from) && at.isBefore(until)) {
                    result.add(new Occurrence(slot.scheduleTimeId(), at));
                }
            }
        }
        result.sort(Comparator.comparing(Occurrence::scheduledAt)
                .thenComparing(o -> String.valueOf(o.scheduleTimeId())));
        return result;
    }
}
