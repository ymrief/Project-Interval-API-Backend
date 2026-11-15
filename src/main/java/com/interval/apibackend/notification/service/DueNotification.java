package com.interval.apibackend.notification.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.interval.apibackend.schedule.entity.DoseUnit;

/**
 * Satu occurrence yang perlu diberi notifikasi, beserta preferensi pemiliknya.
 * Dibentuk langsung oleh query (constructor expression di NotificationDueRepository).
 */
public record DueNotification(
        UUID medicationLogId,
        UUID userId,
        String medicationName,
        BigDecimal doseQuantity,
        DoseUnit doseUnit,
        Instant scheduledAt,
        boolean soundEnabled,
        int reminderBeforeMinutes
) {
}
