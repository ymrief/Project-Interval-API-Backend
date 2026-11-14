package com.interval.apibackend.tracking.listener;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.medication.event.MedicationLifecycleEvent;
import com.interval.apibackend.schedule.entity.MedicationSchedule;
import com.interval.apibackend.schedule.event.ScheduleChangedEvent;
import com.interval.apibackend.schedule.repository.MedicationScheduleRepository;
import com.interval.apibackend.schedule.repository.ScheduleSpecifications;
import com.interval.apibackend.tracking.service.OccurrenceService;
import com.interval.apibackend.user.event.UserTimezoneChangedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Menghubungkan event modul lain ke OccurrenceService. Semua listener sinkron dan berjalan di transaksi
 * pemicunya (MANDATORY), jadi perubahan sumber dan perubahan occurrence-nya atomik: keduanya tersimpan
 * atau keduanya dibatalkan. Arah dependensi tetap satu arah: tracking -> schedule -> medication -> user.
 */
@Component
@RequiredArgsConstructor
public class OccurrenceEventListener {

    private final OccurrenceService occurrenceService;
    private final MedicationScheduleRepository scheduleRepository;

    /** CREATED, UPDATED, dan DELETED ditangani sama: selaraskan. Jadwal terhapus/nonaktif -> semua PENDING dibatalkan. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onScheduleChanged(ScheduleChangedEvent event) {
        occurrenceService.syncSchedule(event.scheduleId());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onMedicationLifecycle(MedicationLifecycleEvent event) {
        switch (event.type()) {
            case PAUSED, DELETED -> occurrenceService.cancelPendingForMedication(event.medicationId());
            case RESUMED -> activeScheduleIds(event.userId(), event.medicationId())
                    .forEach(occurrenceService::syncSchedule);
        }
    }

    /** Jam dibaca di timezone pengguna, jadi semua occurrence mendatang harus dihitung ulang. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onTimezoneChanged(UserTimezoneChangedEvent event) {
        activeScheduleIds(event.userId(), null).forEach(occurrenceService::syncSchedule);
    }

    private java.util.List<UUID> activeScheduleIds(UUID userId, UUID medicationId) {
        return scheduleRepository
                .findAll(ScheduleSpecifications.owned(userId, medicationId, true), Sort.unsorted())
                .stream()
                .map(MedicationSchedule::getId)
                .toList();
    }
}
