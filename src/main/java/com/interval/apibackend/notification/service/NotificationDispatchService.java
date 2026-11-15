package com.interval.apibackend.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.interval.apibackend.notification.entity.Device;
import com.interval.apibackend.notification.entity.NotificationType;
import com.interval.apibackend.notification.push.PushResult;
import com.interval.apibackend.notification.push.PushSender;
import com.interval.apibackend.notification.repository.DeviceRepository;
import com.interval.apibackend.notification.repository.NotificationDueRepository;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.service.OccurrenceProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mengirim notifikasi yang jatuh tempo. Dipanggil job tiap menit. Sengaja TIDAK @Transactional:
 * pengiriman jaringan tidak boleh menahan transaksi; tiap langkah database bertransaksi sendiri
 * (lihat NotificationLogService).
 *
 * Aturan:
 *  - hanya occurrence PENDING milik pengguna yang notificationsEnabled = true;
 *  - MEDICATION_REMINDER: scheduledAt dalam [sekarang - batas MISSED, sekarang]. Batas bawah mencegah
 *    pengingat basi jika dispatcher sempat mati lama;
 *  - PRE_REMINDER: hanya jika reminderBeforeMinutes > 0 dan dosis tinggal <= N menit lagi (belum lewat);
 *  - satu percobaan per (occurrence, perangkat aktif, tipe): QUEUED -> SENT / FAILED. Percobaan yang gagal
 *    TIDAK diulang otomatis (baris sudah ada, uq_notification_once).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    public record DispatchReport(int attempted, int sent, int failed, int skippedDuplicates) {
    }

    private static final class Counters {
        int attempted;
        int sent;
        int failed;
        int skippedDuplicates;
    }

    private final NotificationDueRepository dueRepository;
    private final DeviceRepository deviceRepository;
    private final NotificationLogService logService;
    private final DeviceService deviceService;
    private final PushSender pushSender;
    private final OccurrenceProperties occurrenceProperties;
    private final Clock clock;

    public DispatchReport dispatchDue() {
        Instant now = clock.instant();
        Counters counters = new Counters();

        // 1. Pengingat tepat waktu
        dueRepository.findDueReminders(LogStatus.PENDING, NotificationType.MEDICATION_REMINDER,
                        now.minus(occurrenceProperties.missedAfter()), now)
                .forEach(due -> deliver(due, NotificationType.MEDICATION_REMINDER, counters));

        // 2. Pengingat dini: kandidat sampai batas N maksimum, lalu disaring per pengguna
        Instant leadLimit = now.plus(Duration.ofMinutes(NotificationPreferenceService.MAX_REMINDER_MINUTES));
        dueRepository.findPreReminderCandidates(LogStatus.PENDING, NotificationType.PRE_REMINDER, now, leadLimit)
                .stream()
                .filter(due -> NotificationTiming.preReminderDue(now, due.scheduledAt(), due.reminderBeforeMinutes()))
                .forEach(due -> deliver(due, NotificationType.PRE_REMINDER, counters));

        DispatchReport report = new DispatchReport(counters.attempted, counters.sent, counters.failed,
                counters.skippedDuplicates);
        if (report.attempted() > 0 || report.skippedDuplicates() > 0) {
            log.info("Notification dispatch: {}", report);
        }
        return report;
    }

    private void deliver(DueNotification due, NotificationType type, Counters counters) {
        for (Device device : deviceRepository.findByUserIdAndActiveTrue(due.userId())) {
            UUID notificationLogId;
            try {
                notificationLogId = logService.queue(due.userId(), device.getId(), due.medicationLogId(), type);
            } catch (DataIntegrityViolationException e) {
                counters.skippedDuplicates++;      // sudah diproses (mis. oleh instance lain)
                continue;
            }

            counters.attempted++;
            PushResult result = sendSafely(NotificationMessageFactory.create(type, due, device.getFcmToken()));
            switch (result.outcome()) {
                case SENT -> {
                    logService.markSent(notificationLogId, clock.instant());
                    counters.sent++;
                }
                case INVALID_TOKEN -> {
                    logService.markFailed(notificationLogId, result.error());
                    deviceService.deactivate(device.getId());
                    counters.failed++;
                }
                case FAILED -> {
                    logService.markFailed(notificationLogId, result.error());
                    counters.failed++;
                }
            }
        }
    }

    private PushResult sendSafely(com.interval.apibackend.notification.push.PushMessage message) {
        try {
            return pushSender.send(message);
        } catch (RuntimeException e) {
            return PushResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
