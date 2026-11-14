package com.interval.apibackend.tracking.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.medication.entity.Medication;
import com.interval.apibackend.schedule.entity.MedicationSchedule;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;
import com.interval.apibackend.tracking.repository.MedicationLogRepository;
import com.interval.apibackend.tracking.service.OccurrenceCalculator.Occurrence;
import com.interval.apibackend.tracking.service.OccurrenceCalculator.TimeSlot;
import com.interval.apibackend.user.service.UserTimezoneService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Menyelaraskan (reconcile) occurrence di medication_logs dengan aturan jadwal.
 *
 * Satu operasi untuk SEMUA pemicu (jadwal dibuat/diubah/dihapus, obat dijeda/dilanjutkan, timezone berubah,
 * job berkala): hitung occurrence yang SEHARUSNYA ada dalam jendela [sekarang, sekarang + horizon), lalu bandingkan
 * dengan log mendatang yang sudah ada:
 *
 *   sudah ada & masih sesuai   -> dipertahankan (id tetap); PENDING: snapshot dosis/nama diperbarui;
 *                                 CANCELLED: dihidupkan kembali (PENDING)
 *   sudah ada & tidak sesuai   -> PENDING menjadi CANCELLED (TAKEN/SKIPPED/MISSED tidak pernah disentuh)
 *   belum ada                  -> dibuat PENDING
 *
 * Karena itu operasi ini idempotent. Log yang sudah lewat tidak pernah disentuh, dan tidak ada backfill.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OccurrenceService {

    public record SyncResult(int created, int cancelled, int revived, int refreshed) {
        static final SyncResult NONE = new SyncResult(0, 0, 0, 0);
    }

    private final MedicationLogRepository logRepository;
    private final UserTimezoneService timezoneService;
    private final OccurrenceProperties properties;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public SyncResult syncSchedule(UUID scheduleId) {
        // Kunci baris jadwal: sinkronisasi bersamaan untuk jadwal yang sama (request pengguna vs job)
        // diserialkan, sehingga tidak ada INSERT ganda yang melanggar uq_medication_occurrence.
        MedicationSchedule schedule = entityManager.find(MedicationSchedule.class, scheduleId,
                LockModeType.PESSIMISTIC_WRITE);
        if (schedule == null) {
            return SyncResult.NONE;
        }

        Instant now = clock.instant();
        Instant until = now.plus(properties.horizon());
        Medication medication = schedule.getMedication();

        Set<Occurrence> desired = Set.of();
        if (isEligible(schedule, medication)) {
            ZoneId zone = timezoneService.zoneOf(medication.getUserId());
            List<TimeSlot> slots = schedule.getTimes().stream()
                    .map(t -> new TimeSlot(t.getId(), t.getScheduledTime(), t.getDayOfWeek()))
                    .toList();
            desired = new LinkedHashSet<>(OccurrenceCalculator.compute(
                    schedule.getFrequencyType(), schedule.getIntervalDays(), schedule.getStartDate(),
                    schedule.getEndDate(), slots, zone, now, until));
        }
        return reconcile(schedule, medication, desired, now);
    }

    /** Obat dijeda atau dihapus: batalkan semua occurrence PENDING mendatang miliknya. */
    @Transactional
    public int cancelPendingForMedication(UUID medicationId) {
        Instant now = clock.instant();
        return logRepository.transitionByMedication(medicationId, LogStatus.PENDING, LogStatus.CANCELLED, now, now);
    }

    /** PENDING yang melewati batas toleransi menjadi MISSED. Dipanggil job berkala. */
    @Transactional
    public int markMissed() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.missedAfter());
        int updated = logRepository.transitionOverdue(LogStatus.PENDING, LogStatus.MISSED, cutoff, now);
        if (updated > 0) {
            log.info("Marked {} overdue occurrence(s) as MISSED", updated);
        }
        return updated;
    }

    // ------------------------------------------------------------------ internal

    private static boolean isEligible(MedicationSchedule schedule, Medication medication) {
        return schedule.isActive() && schedule.getDeletedAt() == null
                && medication.isActive() && medication.getDeletedAt() == null;
    }

    private SyncResult reconcile(MedicationSchedule schedule, Medication medication,
                                 Set<Occurrence> desired, Instant now) {
        int cancelled = 0;
        int revived = 0;
        int refreshed = 0;
        Map<Occurrence, MedicationLog> matched = new HashMap<>();

        for (MedicationLog existing : logRepository
                .findByScheduleIdAndScheduledAtGreaterThanEqual(schedule.getId(), now)) {
            // Jam yang sudah dihapus meninggalkan log dengan schedule_time_id NULL (FK ON DELETE SET NULL):
            // tidak mungkin cocok dengan occurrence mana pun, jadi diperlakukan sebagai tidak sesuai.
            Occurrence key = existing.getScheduleTimeId() == null
                    ? null
                    : new Occurrence(existing.getScheduleTimeId(), existing.getScheduledAt());

            if (key != null && desired.contains(key)) {
                matched.put(key, existing);
                if (existing.getStatus() == LogStatus.CANCELLED) {
                    existing.setStatus(LogStatus.PENDING);
                    existing.setActionAt(null);
                    refreshSnapshot(existing, schedule, medication);
                    revived++;
                } else if (existing.getStatus() == LogStatus.PENDING
                        && refreshSnapshot(existing, schedule, medication)) {
                    refreshed++;
                }
            } else if (existing.getStatus() == LogStatus.PENDING) {
                existing.setStatus(LogStatus.CANCELLED);
                cancelled++;
            }
        }

        List<MedicationLog> toCreate = desired.stream()
                .filter(occurrence -> !matched.containsKey(occurrence))
                .map(occurrence -> MedicationLog.pending(
                        medication.getUserId(), medication.getId(), schedule.getId(), occurrence.scheduleTimeId(),
                        medication.getName(), schedule.getDoseQuantity(), schedule.getDoseUnit(),
                        occurrence.scheduledAt()))
                .toList();
        logRepository.saveAll(toCreate);

        SyncResult result = new SyncResult(toCreate.size(), cancelled, revived, refreshed);
        if (result.created() + result.cancelled() + result.revived() + result.refreshed() > 0) {
            log.debug("Synced schedule {}: {}", schedule.getId(), result);
        }
        return result;
    }

    /** Menyamakan snapshot nama/dosis; mengembalikan true jika ada yang berubah. */
    private static boolean refreshSnapshot(MedicationLog log, MedicationSchedule schedule, Medication medication) {
        boolean changed = false;
        if (!Objects.equals(log.getMedicationName(), medication.getName())) {
            log.setMedicationName(medication.getName());
            changed = true;
        }
        if (log.getDoseQuantity().compareTo(schedule.getDoseQuantity()) != 0) {
            log.setDoseQuantity(schedule.getDoseQuantity());
            changed = true;
        }
        if (log.getDoseUnit() != schedule.getDoseUnit()) {
            log.setDoseUnit(schedule.getDoseUnit());
            changed = true;
        }
        return changed;
    }
}
