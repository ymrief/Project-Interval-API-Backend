package com.interval.apibackend.schedule.dto;

import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.schedule.entity.FrequencyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Seluruh validasi (wajib diisi, rentang, lintas-field) ada di ScheduleRules, bukan di anotasi Bean Validation,
 * sehingga semua kegagalan jadwal memakai pesan "Invalid schedule configuration" dan melaporkan semua error sekaligus.
 */
public record CreateScheduleRequest(
        FrequencyType frequencyType,
        Integer intervalDays,
        BigDecimal doseQuantity,
        DoseUnit doseUnit,
        LocalDate startDate,
        LocalDate endDate,
        List<ScheduleTimeRequest> times
) {
}
