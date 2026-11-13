package com.interval.apibackend.schedule.dto;

import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.interval.apibackend.schedule.entity.ScheduleTime;

public record ScheduleTimeResponse(
        UUID id,
        // Pola dipaksa agar detik selalu tampil ("08:00:00"), tidak bergantung konfigurasi Jackson.
        @JsonFormat(pattern = "HH:mm:ss") LocalTime scheduledTime,
        Integer dayOfWeek
) {

    public static ScheduleTimeResponse from(ScheduleTime time) {
        return new ScheduleTimeResponse(time.getId(), time.getScheduledTime(), time.getDayOfWeek());
    }
}
