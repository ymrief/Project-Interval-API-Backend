package com.interval.apibackend.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.interval.apibackend.notification.entity.NotificationType;
import com.interval.apibackend.notification.service.DueNotification;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;

/**
 * Kandidat notifikasi. Kedua query memakai "not exists" pada notification_logs untuk (log, tipe):
 * occurrence yang sudah pernah diproses untuk tipe itu tidak muncul lagi (idempotent), dan pengguna yang
 * mematikan notifikasi tidak pernah muncul sama sekali.
 */
public interface NotificationDueRepository extends Repository<MedicationLog, UUID> {

    /** Pengingat tepat waktu: scheduledAt dalam [rangeStart, rangeEnd]. */
    @Query("""
            select new com.interval.apibackend.notification.service.DueNotification(
                       l.id, l.userId, l.medicationName, l.doseQuantity, l.doseUnit, l.scheduledAt,
                       p.soundEnabled, p.reminderBeforeMinutes)
              from MedicationLog l join NotificationPreference p on p.userId = l.userId
             where l.status = :status and p.notificationsEnabled = true
               and l.scheduledAt >= :rangeStart and l.scheduledAt <= :rangeEnd
               and not exists (select 1 from NotificationLog n
                                where n.medicationLogId = l.id and n.notificationType = :type)
             order by l.scheduledAt asc, l.id asc
            """)
    List<DueNotification> findDueReminders(@Param("status") LogStatus status,
                                           @Param("type") NotificationType type,
                                           @Param("rangeStart") Instant rangeStart,
                                           @Param("rangeEnd") Instant rangeEnd);

    /**
     * Kandidat pengingat dini: hanya pengguna dengan reminderBeforeMinutes > 0, scheduledAt di masa depan
     * sampai batas atas. Saringan akhir per pengguna (scheduledAt - sekarang <= reminderBeforeMinutes)
     * dilakukan di NotificationTiming, karena aritmetika menit per baris tidak portabel di JPQL.
     */
    @Query("""
            select new com.interval.apibackend.notification.service.DueNotification(
                       l.id, l.userId, l.medicationName, l.doseQuantity, l.doseUnit, l.scheduledAt,
                       p.soundEnabled, p.reminderBeforeMinutes)
              from MedicationLog l join NotificationPreference p on p.userId = l.userId
             where l.status = :status and p.notificationsEnabled = true and p.reminderBeforeMinutes > 0
               and l.scheduledAt > :rangeStart and l.scheduledAt <= :rangeEnd
               and not exists (select 1 from NotificationLog n
                                where n.medicationLogId = l.id and n.notificationType = :type)
             order by l.scheduledAt asc, l.id asc
            """)
    List<DueNotification> findPreReminderCandidates(@Param("status") LogStatus status,
                                                    @Param("type") NotificationType type,
                                                    @Param("rangeStart") Instant rangeStart,
                                                    @Param("rangeEnd") Instant rangeEnd);
}
