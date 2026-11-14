package com.interval.apibackend.tracking.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.interval.apibackend.tracking.repository.EligibleScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Memperbarui occurrence SEMUA jadwal yang layak (untuk job berkala).
 *
 * Sengaja kelas terpisah dan TIDAK @Transactional: tiap jadwal diproses lewat proxy OccurrenceService
 * dalam transaksinya sendiri. Satu jadwal yang bermasalah tidak membatalkan yang lain, dan kunci baris
 * dilepas segera setelah tiap jadwal selesai. (Memanggil method @Transactional dari kelas yang sama
 * tidak akan membuka transaksi baru.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OccurrenceBatchService {

    private static final int BATCH_SIZE = 200;

    public record BatchResult(int succeeded, int failed) {
    }

    private final EligibleScheduleRepository eligibleScheduleRepository;
    private final OccurrenceService occurrenceService;
    private final Clock clock;

    public BatchResult generateForAllActive() {
        // Jadwal yang endDate-nya sudah lewat lebih dari 2 hari tidak mungkin punya occurrence mendatang
        // (selisih 2 hari menutup perbedaan timezone).
        LocalDate earliestEnd = LocalDate.now(clock).minusDays(2);
        UUID cursor = new UUID(0L, 0L);
        int succeeded = 0;
        int failed = 0;

        while (true) {
            List<UUID> ids = eligibleScheduleRepository
                    .findEligibleIdsAfter(cursor, earliestEnd, PageRequest.ofSize(BATCH_SIZE));
            if (ids.isEmpty()) {
                break;
            }
            for (UUID id : ids) {
                try {
                    occurrenceService.syncSchedule(id);
                    succeeded++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("Failed to sync occurrences for schedule {}", id, e);
                }
            }
            cursor = ids.get(ids.size() - 1);
            if (ids.size() < BATCH_SIZE) {
                break;
            }
        }
        if (succeeded + failed > 0) {
            log.info("Occurrence generation finished: {} succeeded, {} failed", succeeded, failed);
        }
        return new BatchResult(succeeded, failed);
    }
}
