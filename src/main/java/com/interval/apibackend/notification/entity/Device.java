package com.interval.apibackend.notification.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.interval.apibackend.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Perangkat untuk push notification. userId BISA berpindah: satu perangkat fisik (satu token FCM)
 * dapat dipakai bergantian oleh beberapa akun, dan token itu unik secara global (uq_devices_fcm_token).
 */
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device extends AuditableEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    @Column(name = "fcm_token", nullable = false, length = 512)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "platform", nullable = false, length = 10)
    private DevicePlatform platform;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public static Device create(UUID userId, String fcmToken, DevicePlatform platform, String deviceName,
                                Instant now) {
        Device device = new Device();
        device.userId = userId;
        device.fcmToken = fcmToken;
        device.platform = platform;
        device.deviceName = deviceName;
        device.lastSeenAt = now;
        return device;
    }
}
