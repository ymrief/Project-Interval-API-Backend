package com.interval.apibackend.notification.service;

import java.util.Map;

import com.interval.apibackend.notification.entity.NotificationType;
import com.interval.apibackend.notification.push.PushMessage;
import com.interval.apibackend.schedule.entity.DoseUnit;

/**
 * Teks notifikasi (Bahasa Indonesia, sesuai bahasa aplikasi) dan payload data untuk client.
 * Fungsi murni. Jika nanti butuh banyak bahasa, titik perubahannya hanya di kelas ini.
 */
public final class NotificationMessageFactory {

    private NotificationMessageFactory() {
    }

    public static PushMessage create(NotificationType type, DueNotification due, String token) {
        String dose = doseText(due);
        String title;
        String body;
        switch (type) {
            case MEDICATION_REMINDER -> {
                title = "Waktunya minum obat";
                body = due.medicationName() + ", " + dose;
            }
            case PRE_REMINDER -> {
                title = "Sebentar lagi waktunya minum obat";
                body = due.medicationName() + ", " + dose + " dalam " + due.reminderBeforeMinutes() + " menit";
            }
            case MISSED_ALERT -> {
                title = "Obat terlewat";
                body = due.medicationName() + ", " + dose + " belum diminum";
            }
            default -> throw new IllegalArgumentException("Unsupported notification type: " + type);
        }

        // Semua nilai data FCM harus berupa String.
        Map<String, String> data = Map.of(
                "type", type.name(),
                "medicationLogId", String.valueOf(due.medicationLogId()),
                "scheduledAt", due.scheduledAt().toString(),
                "sound", String.valueOf(due.soundEnabled()));
        return new PushMessage(token, title, body, due.soundEnabled(), data);
    }

    static String doseText(DueNotification due) {
        return due.doseQuantity().stripTrailingZeros().toPlainString() + " " + unitLabel(due.doseUnit());
    }

    private static String unitLabel(DoseUnit unit) {
        return switch (unit) {
            case TABLET -> "tablet";
            case CAPSULE -> "kapsul";
            case ML -> "ml";
            case DROP -> "tetes";
            case OTHER -> "dosis";
        };
    }
}
