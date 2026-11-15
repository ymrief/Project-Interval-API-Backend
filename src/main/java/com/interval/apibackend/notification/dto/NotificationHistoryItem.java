package com.interval.apibackend.notification.dto;

import java.time.ZoneId;
import java.util.UUID;

import com.interval.apibackend.common.util.TimeUtils;
import com.interval.apibackend.notification.entity.DeliveryStatus;
import com.interval.apibackend.notification.entity.NotificationLog;
import com.interval.apibackend.notification.entity.NotificationType;

/** medicationLogId bisa null jika log konsumsi terkait sudah dihapus. */
public record NotificationHistoryItem(
        UUID id,
        UUID medicationLogId,
        NotificationType notificationType,
        DeliveryStatus deliveryStatus,
        String sentAt,
        String errorMessage,
        String createdAt
) {

    public static NotificationHistoryItem from(NotificationLog log, ZoneId zone) {
        return new NotificationHistoryItem(
                log.getId(),
                log.getMedicationLogId(),
                log.getNotificationType(),
                log.getDeliveryStatus(),
                TimeUtils.format(log.getSentAt(), zone),
                log.getErrorMessage(),
                TimeUtils.format(log.getCreatedAt(), zone));
    }
}
