package com.interval.apibackend.notification.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.interval.apibackend.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Satu baris = satu percobaan kirim ke satu perangkat untuk satu occurrence dan satu tipe.
 * Tabel hanya punya created_at (tanpa updated_at), jadi cukup extends BaseEntity.
 * device_id dan medication_log_id nullable: FK-nya ON DELETE SET NULL agar riwayat tidak hilang.
 * uq_notification_once (medication_log_id, device_id, notification_type) mencegah kiriman ganda.
 */
@Entity
@Table(name = "notification_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "device_id", length = 36)
    private UUID deviceId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "medication_log_id", length = 36)
    private UUID medicationLogId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "notification_type", nullable = false, length = 30, updatable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.QUEUED;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    public static NotificationLog queued(UUID userId, UUID deviceId, UUID medicationLogId, NotificationType type) {
        NotificationLog log = new NotificationLog();
        log.userId = userId;
        log.deviceId = deviceId;
        log.medicationLogId = medicationLogId;
        log.notificationType = type;
        return log;
    }

    public void markSent(Instant at) {
        this.deliveryStatus = DeliveryStatus.SENT;
        this.sentAt = at;
        this.errorMessage = null;
    }

    public void markFailed(String error) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.errorMessage = error;
    }
}
