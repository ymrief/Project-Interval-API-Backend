package com.interval.apibackend.schedule.event;

import java.time.Clock;

import com.interval.apibackend.medication.event.MedicationLifecycleEvent;
import com.interval.apibackend.medication.event.MedicationLifecycleEvent.Type;
import com.interval.apibackend.schedule.repository.MedicationScheduleRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Menanggapi perubahan status obat. Berjalan di transaksi yang sama dengan MedicationService. */
@Component
@RequiredArgsConstructor
public class MedicationEventListener {

    private final MedicationScheduleRepository scheduleRepository;
    private final Clock clock;

    // MANDATORY: gagal cepat jika suatu saat event dipublikasikan di luar transaksi.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onMedicationLifecycle(MedicationLifecycleEvent event) {
        if (event.type() == Type.DELETED) {
            scheduleRepository.softDeleteByMedicationId(event.medicationId(), clock.instant());
        }
        // PAUSED/RESUMED: jadwal tidak diubah. Occurrence generator yang membatalkan/membuat ulang occurrence.
    }
}
