package com.interval.apibackend.tracking.dto;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.UUID;

import com.interval.apibackend.common.util.TimeUtils;
import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;

/** Bentuk yang sama untuk list, detail, dan hasil PATCH. medicationId/scheduleId/scheduleTimeId bisa null. */
public record MedicationLogResponse(
        UUID id,
        UUID medicationId,
        String medicationName,
        UUID scheduleId,
        UUID scheduleTimeId,
        BigDecimal doseQuantity,
        DoseUnit doseUnit,
        String scheduledAt,
        LogStatus status,
        String actionAt,
        String notes
) {

    public static MedicationLogResponse from(MedicationLog log, ZoneId zone) {
        return new MedicationLogResponse(
                log.getId(),
                log.getMedicationId(),
                log.getMedicationName(),
                log.getScheduleId(),
                log.getScheduleTimeId(),
                log.getDoseQuantity(),
                log.getDoseUnit(),
                TimeUtils.format(log.getScheduledAt(), zone),
                log.getStatus(),
                TimeUtils.format(log.getActionAt(), zone),
                log.getNotes());
    }
}
