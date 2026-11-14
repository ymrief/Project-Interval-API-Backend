package com.interval.apibackend.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.interval.apibackend.schedule.entity.FrequencyType;
import com.interval.apibackend.tracking.service.OccurrenceCalculator.Occurrence;
import com.interval.apibackend.tracking.service.OccurrenceCalculator.TimeSlot;

/** Inti aplikasi: perhitungan occurrence. Fungsi murni, tanpa Spring dan tanpa Docker. */
class OccurrenceCalculatorTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");     // UTC+7
    private static final ZoneId MAKASSAR = ZoneId.of("Asia/Makassar");   // UTC+8
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static final UUID SLOT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SLOT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static TimeSlot slot(UUID id, int hour, int minute, Integer day) {
        return new TimeSlot(id, LocalTime.of(hour, minute), day);
    }

    private static Instant utc(String value) {
        return Instant.parse(value);
    }

    private static List<Instant> instants(List<Occurrence> occurrences) {
        return occurrences.stream().map(Occurrence::scheduledAt).toList();
    }

    private static List<Occurrence> daily(LocalDate start, LocalDate end, List<TimeSlot> slots,
                                          ZoneId zone, Instant from, Instant until) {
        return OccurrenceCalculator.compute(FrequencyType.DAILY, null, start, end, slots, zone, from, until);
    }

    // ---------------- DAILY ----------------

    @Test
    void daily_withinHorizon_skipsPastTimesOfToday() {
        // Kamis 12 Nov 2026 17:00 WIB; jam 08:00 hari ini sudah lewat.
        List<Occurrence> result = daily(LocalDate.of(2026, 11, 12), null,
                List.of(slot(SLOT_A, 8, 0, null), slot(SLOT_B, 20, 0, null)),
                JAKARTA, utc("2026-11-12T10:00:00Z"), utc("2026-11-14T10:00:00Z"));

        assertThat(instants(result)).containsExactly(
                utc("2026-11-12T13:00:00Z"),   // Kamis 20:00 WIB
                utc("2026-11-13T01:00:00Z"),   // Jumat 08:00 WIB
                utc("2026-11-13T13:00:00Z"),   // Jumat 20:00 WIB
                utc("2026-11-14T01:00:00Z"));  // Sabtu 08:00 WIB
        assertThat(result.get(0).scheduleTimeId()).isEqualTo(SLOT_B);
        assertThat(result.get(1).scheduleTimeId()).isEqualTo(SLOT_A);
    }

    @Test
    void window_isInclusiveAtFrom_andExclusiveAtUntil() {
        List<TimeSlot> slots = List.of(slot(SLOT_A, 8, 0, null));   // 08:00 WIB = 01:00Z
        LocalDate start = LocalDate.of(2026, 11, 1);

        assertThat(daily(start, null, slots, JAKARTA, utc("2026-11-13T01:00:00Z"), utc("2026-11-13T02:00:00Z")))
                .hasSize(1);
        assertThat(daily(start, null, slots, JAKARTA, utc("2026-11-13T01:00:01Z"), utc("2026-11-13T02:00:00Z")))
                .isEmpty();
        assertThat(daily(start, null, slots, JAKARTA, utc("2026-11-13T00:00:00Z"), utc("2026-11-13T01:00:00Z")))
                .isEmpty();
    }

    @Test
    void startAndEndDates_areInclusiveLocalDates() {
        List<TimeSlot> slots = List.of(slot(SLOT_A, 8, 0, null));
        Instant from = utc("2026-11-10T00:00:00Z");
        Instant until = utc("2026-11-20T00:00:00Z");

        // mulai 13 Nov: 11 dan 12 Nov tidak ikut
        assertThat(instants(daily(LocalDate.of(2026, 11, 13), null, slots, JAKARTA, from, utc("2026-11-15T00:00:00Z"))))
                .containsExactly(utc("2026-11-13T01:00:00Z"), utc("2026-11-14T01:00:00Z"));

        // berakhir 12 Nov (inklusif)
        assertThat(instants(daily(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), slots, JAKARTA, from, until)))
                .containsExactly(utc("2026-11-10T01:00:00Z"), utc("2026-11-11T01:00:00Z"), utc("2026-11-12T01:00:00Z"));

        // mulai di masa depan jauh: kosong
        assertThat(daily(LocalDate.of(2027, 1, 1), null, slots, JAKARTA, from, until)).isEmpty();
    }

    // ---------------- WEEKLY ----------------

    @Test
    void weekly_producesOnlyMatchingWeekdays() {
        // Senin (1) dan Kamis (4) pukul 08:00 WIB, jendela 9-23 Nov 2026.
        List<Occurrence> result = OccurrenceCalculator.compute(FrequencyType.WEEKLY, null,
                LocalDate.of(2026, 11, 1), null,
                List.of(slot(SLOT_A, 8, 0, 1), slot(SLOT_B, 8, 0, 4)),
                JAKARTA, utc("2026-11-09T00:00:00Z"), utc("2026-11-23T00:00:00Z"));

        assertThat(instants(result)).containsExactly(
                utc("2026-11-09T01:00:00Z"),   // Senin 9
                utc("2026-11-12T01:00:00Z"),   // Kamis 12
                utc("2026-11-16T01:00:00Z"),   // Senin 16
                utc("2026-11-19T01:00:00Z"));  // Kamis 19
    }

    @Test
    void weekly_slotWithoutDayOfWeek_isIgnored() {
        List<Occurrence> result = OccurrenceCalculator.compute(FrequencyType.WEEKLY, null,
                LocalDate.of(2026, 11, 1), null, List.of(slot(SLOT_A, 8, 0, null)),
                JAKARTA, utc("2026-11-09T00:00:00Z"), utc("2026-11-23T00:00:00Z"));

        assertThat(result).isEmpty();
    }

    // ---------------- INTERVAL ----------------

    @Test
    void interval_isAnchoredOnStartDate() {
        // Setiap 3 hari sejak 1 Nov pukul 09:00 WIB (02:00Z): 1, 4, 7, 10, 13 Nov.
        List<Occurrence> result = OccurrenceCalculator.compute(FrequencyType.INTERVAL, 3,
                LocalDate.of(2026, 11, 1), null, List.of(slot(SLOT_A, 9, 0, null)),
                JAKARTA, utc("2026-11-01T00:00:00Z"), utc("2026-11-15T00:00:00Z"));

        assertThat(instants(result)).containsExactly(
                utc("2026-11-01T02:00:00Z"), utc("2026-11-04T02:00:00Z"), utc("2026-11-07T02:00:00Z"),
                utc("2026-11-10T02:00:00Z"), utc("2026-11-13T02:00:00Z"));
    }

    @Test
    void interval_keepsItsCycle_whenWindowStartsMidCycle() {
        // Jendela mulai 5 Nov: siklus tetap 1, 4, 7, 10, ... (BUKAN dimulai ulang dari hari jendela).
        List<Occurrence> result = OccurrenceCalculator.compute(FrequencyType.INTERVAL, 3,
                LocalDate.of(2026, 11, 1), null, List.of(slot(SLOT_A, 9, 0, null)),
                JAKARTA, utc("2026-11-05T00:00:00Z"), utc("2026-11-14T00:00:00Z"));

        assertThat(instants(result)).containsExactly(
                utc("2026-11-07T02:00:00Z"), utc("2026-11-10T02:00:00Z"), utc("2026-11-13T02:00:00Z"));
    }

    @Test
    void interval_withEndDate_stopsAfterIt() {
        List<Occurrence> result = OccurrenceCalculator.compute(FrequencyType.INTERVAL, 2,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), List.of(slot(SLOT_A, 9, 0, null)),
                JAKARTA, utc("2026-11-01T00:00:00Z"), utc("2026-11-30T00:00:00Z"));

        assertThat(instants(result)).containsExactly(
                utc("2026-11-01T02:00:00Z"), utc("2026-11-03T02:00:00Z"), utc("2026-11-05T02:00:00Z"));
    }

    // ---------------- timezone ----------------

    @Test
    void sameLocalTime_inDifferentTimezones_givesDifferentInstants() {
        List<TimeSlot> slots = List.of(slot(SLOT_A, 8, 0, null));
        LocalDate start = LocalDate.of(2026, 11, 13);
        Instant from = utc("2026-11-13T00:00:00Z");
        Instant until = utc("2026-11-13T12:00:00Z");

        assertThat(instants(daily(start, null, slots, JAKARTA, from, until))).containsExactly(utc("2026-11-13T01:00:00Z"));
        assertThat(instants(daily(start, null, slots, MAKASSAR, from, until))).containsExactly(utc("2026-11-13T00:00:00Z"));
    }

    @Test
    void localDate_canDifferFromUtcDate() {
        // 06:00 WIB tanggal 13 Nov = 23:00Z tanggal 12 Nov. Tidak boleh bergeser sehari.
        List<Occurrence> result = daily(LocalDate.of(2026, 11, 13), null,
                List.of(slot(SLOT_A, 6, 0, null)), JAKARTA,
                utc("2026-11-12T00:00:00Z"), utc("2026-11-13T00:00:00Z"));

        assertThat(instants(result)).containsExactly(utc("2026-11-12T23:00:00Z"));
    }

    @Test
    void weekly_usesTheLocalWeekday_notTheUtcWeekday() {
        // Jumat (5) 06:00 WIB = Kamis 23:00Z. Hari lokalnya tetap Jumat.
        List<Occurrence> result = OccurrenceCalculator.compute(FrequencyType.WEEKLY, null,
                LocalDate.of(2026, 11, 1), null, List.of(slot(SLOT_A, 6, 0, 5)),
                JAKARTA, utc("2026-11-12T00:00:00Z"), utc("2026-11-14T00:00:00Z"));

        assertThat(instants(result)).containsExactly(utc("2026-11-12T23:00:00Z"));
    }

    // ---------------- DST ----------------

    @Test
    void dst_springForward_nonexistentTimeMovesForward() {
        // 8 Mar 2026: 02:30 tidak ada di New York (02:00 -> 03:00), jadi menjadi 03:30 EDT = 07:30Z.
        List<Occurrence> result = daily(LocalDate.of(2026, 3, 7), null,
                List.of(slot(SLOT_A, 2, 30, null)), NEW_YORK,
                utc("2026-03-07T00:00:00Z"), utc("2026-03-10T00:00:00Z"));

        assertThat(instants(result)).containsExactly(
                utc("2026-03-07T07:30:00Z"),   // EST (-5)
                utc("2026-03-08T07:30:00Z"),   // hari DST: 03:30 EDT
                utc("2026-03-09T06:30:00Z"));  // EDT (-4)
    }

    @Test
    void dst_fallBack_ambiguousTimeUsesEarlierOffset() {
        // 1 Nov 2026: 01:30 terjadi dua kali; dipakai yang pertama (EDT, -4) = 05:30Z.
        List<Occurrence> result = daily(LocalDate.of(2026, 10, 31), null,
                List.of(slot(SLOT_A, 1, 30, null)), NEW_YORK,
                utc("2026-10-31T00:00:00Z"), utc("2026-11-03T00:00:00Z"));

        assertThat(instants(result)).containsExactly(
                utc("2026-10-31T05:30:00Z"),
                utc("2026-11-01T05:30:00Z"),
                utc("2026-11-02T06:30:00Z"));  // sudah EST (-5)
    }

    // ---------------- input tidak valid ----------------

    @Test
    void invalidInputs_produceNoOccurrences() {
        List<TimeSlot> slots = List.of(slot(SLOT_A, 8, 0, null));
        LocalDate start = LocalDate.of(2026, 11, 1);
        Instant now = utc("2026-11-12T00:00:00Z");

        assertThat(daily(start, null, slots, JAKARTA, now, now)).isEmpty();                       // jendela kosong
        assertThat(daily(start, null, slots, JAKARTA, now.plusSeconds(60), now)).isEmpty();       // terbalik
        assertThat(daily(start, null, List.of(), JAKARTA, now, now.plusSeconds(86400))).isEmpty(); // tanpa jam
        assertThat(OccurrenceCalculator.compute(FrequencyType.INTERVAL, null, start, null, slots, JAKARTA,
                now, now.plusSeconds(86400 * 3))).isEmpty();
        assertThat(OccurrenceCalculator.compute(FrequencyType.INTERVAL, 0, start, null, slots, JAKARTA,
                now, now.plusSeconds(86400 * 3))).isEmpty();
        assertThat(OccurrenceCalculator.compute(null, null, start, null, slots, JAKARTA,
                now, now.plusSeconds(86400 * 3))).isEmpty();
    }

    @Test
    void results_areSortedByTime_thenBySlotId() {
        // Dua jam bertepatan: urut berdasarkan id slot agar hasil deterministik.
        List<Occurrence> result = daily(LocalDate.of(2026, 11, 13), null,
                List.of(slot(SLOT_B, 8, 0, null), slot(SLOT_A, 8, 0, null)), JAKARTA,
                utc("2026-11-13T00:00:00Z"), utc("2026-11-14T00:00:00Z"));

        assertThat(result).extracting(Occurrence::scheduleTimeId).containsExactly(SLOT_A, SLOT_B);
    }
}