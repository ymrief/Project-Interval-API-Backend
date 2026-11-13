package com.interval.apibackend.schedule.dto;

import java.time.LocalTime;

/** Tanpa anotasi validasi: semua aturan jadwal dicek di ScheduleRules agar pesan dan kuncinya seragam. */
public record ScheduleTimeRequest(LocalTime scheduledTime, Integer dayOfWeek) {
}
