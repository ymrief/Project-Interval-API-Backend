package com.interval.apibackend.schedule.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.schedule.entity.FrequencyType;
import com.interval.apibackend.schedule.entity.MedicationSchedule;
import com.interval.apibackend.schedule.entity.ScheduleTime;

/** Bentuk yang sama untuk list, detail, create, dan update. */
public record ScheduleResponse(
        UUID id,
        UUID medicationId,
        FrequencyType frequencyType,
        Integer intervalDays,
        BigDecimal doseQuantity,
        DoseUnit doseUnit,
        LocalDate startDate,
        LocalDate endDate,
        @JsonProperty("isActive") boolean active,
        List<ScheduleTimeResponse> times
) {

    // Urut per hari lalu jam agar response deterministik (urutan di database tidak dijamin).
    private static final Comparator<ScheduleTime> TIME_ORDER = Comparator
            .comparing((ScheduleTime t) -> t.getDayOfWeek() == null ? 0 : t.getDayOfWeek())
            .thenComparing(ScheduleTime::getScheduledTime);

    public static ScheduleResponse from(MedicationSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getMedication().getId(),
                schedule.getFrequencyType(),
                schedule.getIntervalDays(),
                schedule.getDoseQuantity(),
                schedule.getDoseUnit(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.isActive(),
                schedule.getTimes().stream().sorted(TIME_ORDER).map(ScheduleTimeResponse::from).toList());
    }
}
