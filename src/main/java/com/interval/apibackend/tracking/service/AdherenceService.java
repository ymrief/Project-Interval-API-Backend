package com.interval.apibackend.tracking.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.interval.apibackend.tracking.service.LogDateRange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.tracking.dto.AdherenceResponse;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.repository.MedicationLogRepository;
import com.interval.apibackend.tracking.repository.MedicationLogRepository.StatusCount;
import com.interval.apibackend.user.service.UserTimezoneService;

import lombok.RequiredArgsConstructor;

/** Perhitungan adherence. Dipakai endpoint Tracking dan akan dipakai Dashboard. */
@Service
@RequiredArgsConstructor
public class AdherenceService {

    private final MedicationLogRepository logRepository;
    private final UserTimezoneService timezoneService;
    private final Clock clock;

    /** Untuk endpoint: from/to opsional, tanggal lokal pengguna. */
    @Transactional(readOnly = true)
    public AdherenceResponse forDates(UUID userId, LocalDate from, LocalDate to, UUID medicationId) {
        ZoneId zone = timezoneService.zoneOf(userId);
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        LogDateRange range = LogDateRange.resolve(from, to, zone, today);
        return forInstants(userId, range.start(), range.endExclusive(), medicationId);
    }

    /** Untuk pemanggil internal yang sudah punya batas Instant (mis. Dashboard). medicationId boleh null. */
    @Transactional(readOnly = true)
    public AdherenceResponse forInstants(UUID userId, Instant start, Instant endExclusive, UUID medicationId) {
        List<StatusCount> counts = medicationId == null
                ? logRepository.countByStatus(userId, start, endExclusive)
                : logRepository.countByStatusForMedication(userId, medicationId, start, endExclusive);

        Map<LogStatus, Long> byStatus = new EnumMap<>(LogStatus.class);
        counts.forEach(count -> byStatus.put(count.getStatus(), count.getTotal()));

        // CANCELLED sengaja diabaikan: bukan bagian dari jadwal yang berlaku.
        return AdherenceResponse.of(
                byStatus.getOrDefault(LogStatus.TAKEN, 0L),
                byStatus.getOrDefault(LogStatus.SKIPPED, 0L),
                byStatus.getOrDefault(LogStatus.MISSED, 0L),
                byStatus.getOrDefault(LogStatus.PENDING, 0L));
    }
}
