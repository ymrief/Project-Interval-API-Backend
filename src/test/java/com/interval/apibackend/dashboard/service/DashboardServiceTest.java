package com.interval.apibackend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.interval.apibackend.dashboard.dto.DashboardSummaryResponse;
import com.interval.apibackend.dashboard.dto.TodayScheduleItem;
import com.interval.apibackend.dashboard.repository.DashboardLogRepository;
import com.interval.apibackend.dashboard.repository.DashboardMedicationRepository;
import com.interval.apibackend.dashboard.service.DashboardService;
import com.interval.apibackend.medication.entity.DosageForm;
import com.interval.apibackend.medication.entity.Medication;
import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.tracking.dto.AdherenceResponse;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;
import com.interval.apibackend.tracking.service.AdherenceService;
import com.interval.apibackend.user.service.UserTimezoneService;

/** Memastikan batas waktu yang dikirim ke query benar (hari lokal, jendela 7 hari). Tanpa Spring dan tanpa Docker. */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private DashboardLogRepository logRepository;
    @Mock
    private DashboardMedicationRepository medicationRepository;
    @Mock
    private AdherenceService adherenceService;
    @Mock
    private UserTimezoneService timezoneService;

    private DashboardService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new DashboardService(logRepository, medicationRepository, adherenceService, timezoneService, clock);
    }

    @BeforeEach
    void stubTimezone() {
        when(timezoneService.zoneOf(USER)).thenReturn(JAKARTA);
    }

    // ---------------- summary ----------------

    @Test
    void summary_mapsTodayCounts_andWeeklyAdherence_overTheRightWindows() {
        // Kamis 12 Nov 2026 17:00 WIB. Hari ini = 12 Nov; 7 hari = 6..12 Nov.
        Instant dayStart = Instant.parse("2026-11-11T17:00:00Z");
        Instant dayEnd = Instant.parse("2026-11-12T17:00:00Z");
        Instant weekStart = Instant.parse("2026-11-05T17:00:00Z");

        when(medicationRepository.countByUserIdAndActiveTrueAndDeletedAtIsNull(USER)).thenReturn(3L);
        when(adherenceService.forInstants(eq(USER), eq(dayStart), eq(dayEnd), isNull()))
                .thenReturn(AdherenceResponse.of(2, 0, 1, 3));                       // contoh di dashboard.md
        when(adherenceService.forInstants(eq(USER), eq(weekStart), eq(dayEnd), isNull()))
                .thenReturn(AdherenceResponse.of(8, 1, 1, 0));                       // 8/10 = 80.00

        DashboardSummaryResponse summary = serviceAt("2026-11-12T10:00:00Z").summary(USER);

        assertThat(summary.activeMedicationCount()).isEqualTo(3);
        assertThat(summary.todayScheduledCount()).isEqualTo(6);
        assertThat(summary.todayTakenCount()).isEqualTo(2);
        assertThat(summary.todaySkippedCount()).isZero();
        assertThat(summary.todayPendingCount()).isEqualTo(3);
        assertThat(summary.todayMissedCount()).isEqualTo(1);
        assertThat(summary.weeklyAdherencePercentage()).isEqualByComparingTo(new BigDecimal("80.00"));
        // jumlah per status harus sama dengan total (tidak ada selisih seperti contoh lama)
        assertThat(summary.todayTakenCount() + summary.todaySkippedCount()
                + summary.todayPendingCount() + summary.todayMissedCount())
                .isEqualTo(summary.todayScheduledCount());
    }

    @Test
    void summary_usesTheLocalDate_notTheUtcDate() {
        // 17:30 UTC tanggal 12 Nov = 00:30 WIB tanggal 13 Nov: "hari ini" adalah 13 Nov.
        Instant dayStart = Instant.parse("2026-11-12T17:00:00Z");     // 13 Nov 00:00 WIB
        Instant dayEnd = Instant.parse("2026-11-13T17:00:00Z");       // 14 Nov 00:00 WIB
        Instant weekStart = Instant.parse("2026-11-06T17:00:00Z");    // 7 Nov 00:00 WIB

        when(medicationRepository.countByUserIdAndActiveTrueAndDeletedAtIsNull(USER)).thenReturn(0L);
        when(adherenceService.forInstants(eq(USER), eq(dayStart), eq(dayEnd), isNull()))
                .thenReturn(AdherenceResponse.of(0, 0, 0, 0));
        when(adherenceService.forInstants(eq(USER), eq(weekStart), eq(dayEnd), isNull()))
                .thenReturn(AdherenceResponse.of(0, 0, 0, 0));

        DashboardSummaryResponse summary = serviceAt("2026-11-12T17:30:00Z").summary(USER);

        assertThat(summary.todayScheduledCount()).isZero();
        assertThat(summary.weeklyAdherencePercentage()).isNull();      // tidak ada yang selesai: null, bukan 0
    }

    // ---------------- today ----------------

    @Test
    void today_enrichesWithMedicationDetails_andToleratesLogsWithoutMedication() {
        UUID medicationId = UUID.randomUUID();
        Medication medication = Medication.create(USER, "Paracetamol", "500 mg", DosageForm.TABLET,
                "Diminum setelah makan", null, null);
        ReflectionTestUtils.setField(medication, "id", medicationId);

        MedicationLog withMedication = log(medicationId, "Paracetamol", "2026-11-12T11:00:00Z");
        MedicationLog orphan = log(null, "Obat lama", "2026-11-12T12:00:00Z");   // obat sudah dihapus permanen

        when(logRepository.findForRange(eq(USER), eq(Instant.parse("2026-11-11T17:00:00Z")),
                eq(Instant.parse("2026-11-12T17:00:00Z")), eq(LogStatus.CANCELLED)))
                .thenReturn(List.of(withMedication, orphan));
        when(medicationRepository.findByIdIn(any())).thenReturn(List.of(medication));

        List<TodayScheduleItem> items = serviceAt("2026-11-12T10:00:00Z").today(USER);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).medicationName()).isEqualTo("Paracetamol");
        assertThat(items.get(0).dosage()).isEqualTo("500 mg");
        assertThat(items.get(0).instructions()).isEqualTo("Diminum setelah makan");
        assertThat(items.get(0).scheduledAt()).isEqualTo("2026-11-12T18:00:00+07:00");
        assertThat(items.get(0).status()).isEqualTo(LogStatus.PENDING);
        assertThat(items.get(0).actionAt()).isNull();

        assertThat(items.get(1).medicationName()).isEqualTo("Obat lama");   // dari snapshot log
        assertThat(items.get(1).dosage()).isNull();
        assertThat(items.get(1).instructions()).isNull();
    }

    @Test
    void today_withNoLogs_returnsEmptyList_withoutQueryingMedications() {
        when(logRepository.findForRange(eq(USER), any(), any(), eq(LogStatus.CANCELLED))).thenReturn(List.of());

        assertThat(serviceAt("2026-11-12T10:00:00Z").today(USER)).isEmpty();
        verify(medicationRepository, never()).findByIdIn(any());
    }

    private static MedicationLog log(UUID medicationId, String name, String scheduledAt) {
        MedicationLog log = MedicationLog.pending(USER, medicationId, UUID.randomUUID(), UUID.randomUUID(),
                name, new BigDecimal("1.00"), DoseUnit.TABLET, Instant.parse(scheduledAt));
        ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
        return log;
    }
}
