package com.interval.apibackend.notification.entity;

import java.util.UUID;

import com.interval.apibackend.common.entity.AuditableEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference extends AuditableEntity {

    // Kolom UUID biasa (bukan relasi) wajib @JdbcTypeCode(CHAR) karena database memakai CHAR(36).
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @Column(name = "sound_enabled", nullable = false)
    private boolean soundEnabled = true;

    @Column(name = "reminder_before_minutes", nullable = false)
    private int reminderBeforeMinutes = 0;

    public static NotificationPreference defaultsFor(UUID userId) {
        NotificationPreference preference = new NotificationPreference();
        preference.userId = userId;
        return preference;
    }
}
