package com.interval.apibackend.dashboard.dto;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.UUID;

import com.interval.apibackend.common.util.TimeUtils;
import com.interval.apibackend.medication.entity.Medication;
import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;

/**
 * medicationName, doseQuantity, doseUnit berasal dari snapshot log (apa yang berlaku saat occurrence dibuat).
 * dosage dan instructions diambil dari obat saat ini; null jika obatnya sudah tidak ada.
 * Untuk mengubah status, client memakai PATCH /api/medication-logs/{logId}.
 */
public record TodayScheduleItem(
        UUID logId,
        UUID medicationId,
        String medicationName,
        String dosage,
        BigDecimal doseQuantity,
        DoseUnit doseUnit,
        String scheduledAt,
        LogStatus status,
        String actionAt,
        String instructions
) {

    public static TodayScheduleItem from(MedicationLog log, Medication medication, ZoneId zone) {
        return new TodayScheduleItem(
                log.getId(),
                log.getMedicationId(),
                log.getMedicationName(),
                medication != null ? medication.getDosage() : null,
                log.getDoseQuantity(),
                log.getDoseUnit(),
                TimeUtils.format(log.getScheduledAt(), zone),
                log.getStatus(),
                TimeUtils.format(log.getActionAt(), zone),
                medication != null ? medication.getInstructions() : null);
    }
}
