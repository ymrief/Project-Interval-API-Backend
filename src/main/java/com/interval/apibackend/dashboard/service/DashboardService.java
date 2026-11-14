package com.interval.apibackend.dashboard.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.common.util.TimeUtils;
import com.interval.apibackend.dashboard.dto.DashboardSummaryResponse;
import com.interval.apibackend.dashboard.dto.TodayScheduleItem;
import com.interval.apibackend.dashboard.repository.DashboardLogRepository;
import com.interval.apibackend.dashboard.repository.DashboardMedicationRepository;
import com.interval.apibackend.medication.entity.Medication;
import com.interval.apibackend.tracking.dto.AdherenceResponse;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;
import com.interval.apibackend.tracking.service.AdherenceService;
import com.interval.apibackend.user.service.UserTimezoneService;

import lombok.RequiredArgsConstructor;

/**
 * Hanya agregasi di atas data yang sudah ada; tidak ada aturan bisnis baru.
 * "Hari ini" selalu tanggal LOKAL pengguna saat request (bukan tanggal UTC server).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** Jendela adherence mingguan: 7 hari lokal termasuk hari ini. */
    static final int ADHERENCE_DAYS = 7;

    private final DashboardLogRepository logRepository;
    private final DashboardMedicationRepository medicationRepository;
    private final AdherenceService adherenceService;
    private final UserTimezoneService timezoneService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(UUID userId) {
        ZoneId zone = timezoneService.zoneOf(userId);
        LocalDate today = today(zone);
        Instant dayStart = TimeUtils.startOfDay(today, zone);
        Instant dayEnd = TimeUtils.startOfNextDay(today, zone);
        Instant weekStart = TimeUtils.startOfDay(today.minusDays(ADHERENCE_DAYS - 1L), zone);

        // forInstants sudah mengecualikan CANCELLED dan memakai rumus adherence yang sama dengan modul Tracking.
        AdherenceResponse todayStats = adherenceService.forInstants(userId, dayStart, dayEnd, null);
        AdherenceResponse weekStats = adherenceService.forInstants(userId, weekStart, dayEnd, null);

        return new DashboardSummaryResponse(
                medicationRepository.countByUserIdAndActiveTrueAndDeletedAtIsNull(userId),
                todayStats.totalScheduled(),
                todayStats.taken(),
                todayStats.skipped(),
                todayStats.pending(),
                todayStats.missed(),
                weekStats.adherencePercentage());
    }

    @Transactional(readOnly = true)
    public List<TodayScheduleItem> today(UUID userId) {
        ZoneId zone = timezoneService.zoneOf(userId);
        LocalDate today = today(zone);

        List<MedicationLog> logs = logRepository.findForRange(userId,
                TimeUtils.startOfDay(today, zone), TimeUtils.startOfNextDay(today, zone), LogStatus.CANCELLED);

        // Satu query tambahan untuk semua obat (bukan satu per baris).
        Set<UUID> medicationIds = logs.stream()
                .map(MedicationLog::getMedicationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Medication> medications = medicationIds.isEmpty()
                ? Map.of()
                : medicationRepository.findByIdIn(medicationIds).stream()
                        .collect(Collectors.toMap(Medication::getId, Function.identity()));

        return logs.stream()
                .map(log -> TodayScheduleItem.from(
                        log,
                        log.getMedicationId() == null ? null : medications.get(log.getMedicationId()),
                        zone))
                .toList();
    }

    private LocalDate today(ZoneId zone) {
        return clock.instant().atZone(zone).toLocalDate();
    }
}
