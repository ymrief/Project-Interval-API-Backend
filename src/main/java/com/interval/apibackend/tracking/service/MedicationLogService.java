package com.interval.apibackend.tracking.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.common.exception.BusinessRuleException;
import com.interval.apibackend.common.exception.NotFoundException;
import com.interval.apibackend.common.response.PageResponse;
import com.interval.apibackend.tracking.dto.MedicationLogResponse;
import com.interval.apibackend.tracking.dto.UpdateMedicationLogRequest;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;
import com.interval.apibackend.tracking.repository.LogSpecifications;
import com.interval.apibackend.tracking.repository.MedicationLogRepository;
import com.interval.apibackend.user.service.UserTimezoneService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MedicationLogService {

    private final MedicationLogRepository logRepository;
    private final UserTimezoneService timezoneService;
    private final OccurrenceProperties occurrenceProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<MedicationLogResponse> list(UUID userId, LocalDate from, LocalDate to, LogStatus status,
                                                    UUID medicationId, int page, int size) {
        ZoneId zone = timezoneService.zoneOf(userId);
        LogDateRange range = LogDateRange.resolve(from, to, zone, today(zone));

        // Terbaru dulu; id sebagai pembeda agar urutan antar halaman stabil.
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "scheduledAt").and(Sort.by("id")));
        Page<MedicationLog> result = logRepository.findAll(
                LogSpecifications.filter(userId, range.start(), range.endExclusive(), status, medicationId), pageable);

        return PageResponse.from(result, log -> MedicationLogResponse.from(log, zone));
    }

    @Transactional(readOnly = true)
    public MedicationLogResponse get(UUID userId, UUID logId) {
        return MedicationLogResponse.from(find(userId, logId), timezoneService.zoneOf(userId));
    }

    @Transactional
    public MedicationLogResponse updateStatus(UUID userId, UUID logId, UpdateMedicationLogRequest request) {
        MedicationLog log = find(userId, logId);
        LogStatus current = log.getStatus();
        LogStatus target = request.getStatus();
        Instant now = clock.instant();

        LogStatusTransitions.violation(current, target, log.getScheduledAt(), now, occurrenceProperties.missedAfter())
                .ifPresent(message -> {
                    throw new BusinessRuleException("Invalid status transition", "status", message);
                });

        if (target != current) {
            log.setStatus(target);
            // actionAt diisi backend: waktu tindakan pengguna. Dikosongkan jika dibatalkan kembali ke PENDING.
            log.setActionAt(target == LogStatus.PENDING ? null : now);
        }
        if (request.isNotesPresent()) {
            log.setNotes(blankToNull(request.getNotes()));
        }

        // flush agar updatedAt ikut terbaru; tidak ada UPDATE jika tidak ada yang berubah
        logRepository.saveAndFlush(log);
        return MedicationLogResponse.from(log, timezoneService.zoneOf(userId));
    }

    private MedicationLog find(UUID userId, UUID logId) {
        // Milik orang lain atau tidak ada: 404 yang sama. Log obat yang sudah dihapus tetap terlihat (riwayat).
        return logRepository.findByIdAndUserId(logId, userId)
                .orElseThrow(() -> new NotFoundException("Medication log not found"));
    }

    private LocalDate today(ZoneId zone) {
        return clock.instant().atZone(zone).toLocalDate();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
