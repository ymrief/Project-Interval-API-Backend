package com.interval.apibackend.notification.service;

import java.time.Duration;
import java.time.Instant;

/** Aturan waktu notifikasi. Fungsi murni agar batasnya mudah diuji. */
public final class NotificationTiming {

    private NotificationTiming() {
    }

    /**
     * Pengingat dini jatuh tempo jika dosis belum tiba (scheduledAt > now) dan tinggal <= minutes menit lagi.
     * Jika sudah lewat waktu dosis, pengingat tepat waktu yang berlaku, jadi pengingat dini dilewati.
     */
    public static boolean preReminderDue(Instant now, Instant scheduledAt, int minutes) {
        return minutes > 0
                && scheduledAt.isAfter(now)
                && !scheduledAt.isAfter(now.plus(Duration.ofMinutes(minutes)));
    }
}
