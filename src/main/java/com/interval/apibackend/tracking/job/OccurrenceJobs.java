package com.interval.apibackend.tracking.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.interval.apibackend.tracking.service.OccurrenceBatchService;
import com.interval.apibackend.tracking.service.OccurrenceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dua job berkala. Dimatikan dengan interval.scheduling.enabled=false (mis. di test).
 *
 * Catatan produksi: jika aplikasi dijalankan lebih dari satu instance, kedua job berjalan di semua instance.
 * Aman (idempotent + kunci baris), hanya boros; gunakan ShedLock bila perlu satu eksekutor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "interval.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OccurrenceJobs {

    private final OccurrenceBatchService batchService;
    private final OccurrenceService occurrenceService;

    /** Menjaga jendela occurrence tetap bergulir ke depan, dan memulihkan jika aplikasi sempat mati. */
    @Scheduled(initialDelayString = "${interval.occurrence.generate-initial-delay:PT30S}",
            fixedDelayString = "${interval.occurrence.generate-interval:PT1H}")
    public void generateUpcomingOccurrences() {
        batchService.generateForAllActive();
    }

    @Scheduled(initialDelayString = "${interval.occurrence.missed-initial-delay:PT1M}",
            fixedDelayString = "${interval.occurrence.missed-check-interval:PT1M}")
    public void markMissedOccurrences() {
        occurrenceService.markMissed();
    }
}
