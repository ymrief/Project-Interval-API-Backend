package com.interval.apibackend.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.interval.apibackend.support.AuthTestHelper;
import com.interval.apibackend.support.AuthTestHelper.Session;
import com.interval.apibackend.support.MutableClock;
import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;
import com.interval.apibackend.tracking.repository.MedicationLogRepository;
import com.interval.apibackend.tracking.service.OccurrenceBatchService;
import com.interval.apibackend.tracking.service.OccurrenceBatchService.BatchResult;
import com.interval.apibackend.tracking.service.OccurrenceService;
import com.jayway.jsonpath.JsonPath;

/**
 * Occurrence generator end-to-end terhadap MySQL asli (butuh Docker), dengan jam yang dikendalikan test.
 *
 * "Sekarang" = Kamis 12 Nov 2026 10:00 UTC = 17:00 WIB. Horizon 48 jam, jadi jendela = sampai Sabtu 14 Nov 10:00 UTC.
 * Jadwal DAILY 08:00 dan 20:00 WIB (mulai 12 Nov) menghasilkan tepat empat occurrence:
 *   J1 = Kamis 20:00 WIB,  J2 = Jumat 08:00 WIB,  J3 = Jumat 20:00 WIB,  J4 = Sabtu 08:00 WIB.
 * (08:00 WIB hari Kamis sudah lewat, dan Sabtu 20:00 WIB di luar jendela.)
 *
 * Job terjadwal dimatikan; test memanggil service-nya langsung agar deterministik.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(OccurrenceIntegrationTest.ClockConfig.class)
@TestPropertySource(properties = "interval.scheduling.enabled=false")
class OccurrenceIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant J1 = Instant.parse("2026-11-12T13:00:00Z");
    private static final Instant J2 = Instant.parse("2026-11-13T01:00:00Z");
    private static final Instant J3 = Instant.parse("2026-11-13T13:00:00Z");
    private static final Instant J4 = Instant.parse("2026-11-14T01:00:00Z");

    private static final String DAILY_BODY = """
            {"frequencyType":"DAILY","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-12",
             "times":[{"scheduledTime":"08:00:00"},{"scheduledTime":"20:00:00"}]}""";

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        // Nama bean berbeda dari "clock" (AppConfig) agar tidak bentrok; @Primary membuatnya dipakai semua bean.
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(BASE);
        }
    }

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MedicationLogRepository logRepository;

    @Autowired
    private OccurrenceBatchService batchService;

    @Autowired
    private OccurrenceService occurrenceService;

    @BeforeEach
    void resetClock() {
        clock.set(BASE);
    }

    // ---------------- pembuatan ----------------

    @Test
    void creatingDailySchedule_generatesPendingOccurrencesWithSnapshots() throws Exception {
        Setup setup = dailySetup();

        List<MedicationLog> logs = logsOf(setup.scheduleId());

        assertThat(logs).extracting(MedicationLog::getScheduledAt).containsExactly(J1, J2, J3, J4);
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.getStatus()).isEqualTo(LogStatus.PENDING);
            assertThat(log.getMedicationName()).isEqualTo("Paracetamol");
            assertThat(log.getDoseQuantity()).isEqualByComparingTo("1");
            assertThat(log.getDoseUnit()).isEqualTo(DoseUnit.TABLET);
            assertThat(log.getMedicationId()).isEqualTo(UUID.fromString(setup.medicationId()));
            assertThat(log.getScheduleTimeId()).isNotNull();
            assertThat(log.getActionAt()).isNull();
        });
    }

    @Test
    void weeklyAndIntervalSchedules_produceTheRightOccurrences() throws Exception {
        Session session = login();
        String medicationId = createMedication(session);

        // Kamis 20:00 dan Jumat 08:00 WIB
        String weekly = createSchedule(session, medicationId, """
                {"frequencyType":"WEEKLY","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-01",
                 "times":[{"scheduledTime":"20:00:00","dayOfWeek":4},{"scheduledTime":"08:00:00","dayOfWeek":5}]}""");
        assertThat(logsOf(weekly)).extracting(MedicationLog::getScheduledAt).containsExactly(J1, J2);

        // Setiap 2 hari sejak 11 Nov pukul 09:00 WIB: 11, 13, 15 ... -> hanya 13 Nov yang masuk jendela
        String interval = createSchedule(session, medicationId, """
                {"frequencyType":"INTERVAL","intervalDays":2,"doseQuantity":1,"doseUnit":"TABLET",
                 "startDate":"2026-11-11","times":[{"scheduledTime":"09:00:00"}]}""");
        assertThat(logsOf(interval)).extracting(MedicationLog::getScheduledAt)
                .containsExactly(Instant.parse("2026-11-13T02:00:00Z"));
    }

    @Test
    void futureStartDate_getsOccurrencesOnlyWhenTheHorizonReachesIt() throws Exception {
        Session session = login();
        String scheduleId = createSchedule(session, createMedication(session), """
                {"frequencyType":"DAILY","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-20",
                 "times":[{"scheduledTime":"08:00:00"}]}""");
        assertThat(logsOf(scheduleId)).isEmpty();

        clock.set(Instant.parse("2026-11-19T10:00:00Z"));
        batchService.generateForAllActive();

        assertThat(logsOf(scheduleId)).extracting(MedicationLog::getScheduledAt).containsExactly(
                Instant.parse("2026-11-20T01:00:00Z"), Instant.parse("2026-11-21T01:00:00Z"));
    }

    // ---------------- job berkala ----------------

    @Test
    void generation_isIdempotent() throws Exception {
        Setup setup = dailySetup();
        List<UUID> idsBefore = logsOf(setup.scheduleId()).stream().map(MedicationLog::getId).toList();

        BatchResult first = batchService.generateForAllActive();
        BatchResult second = batchService.generateForAllActive();

        assertThat(first.failed()).isZero();
        assertThat(second.failed()).isZero();
        assertThat(logsOf(setup.scheduleId()).stream().map(MedicationLog::getId).toList())
                .containsExactlyElementsOf(idsBefore);
    }

    @Test
    void rollingGeneration_extendsTheHorizonAsTimePasses() throws Exception {
        Setup setup = dailySetup();

        clock.advance(Duration.ofHours(24));
        batchService.generateForAllActive();

        assertThat(logsOf(setup.scheduleId())).extracting(MedicationLog::getScheduledAt).containsExactly(
                J1, J2, J3, J4,
                Instant.parse("2026-11-14T13:00:00Z"),    // Sabtu 20:00 WIB
                Instant.parse("2026-11-15T01:00:00Z"));   // Minggu 08:00 WIB
    }

    @Test
    void markMissed_affectsOnlyOverduePending_withStrictCutoff() throws Exception {
        Setup setup = dailySetup();
        List<MedicationLog> logs = logsOf(setup.scheduleId());
        MedicationLog taken = logs.get(0);                    // J1 sudah diminum
        taken.setStatus(LogStatus.TAKEN);
        taken.setActionAt(J1);
        logRepository.save(taken);

        // Batas toleransi 60 menit: pada 14:00Z, cutoff = 13:00Z. J3 (13:00Z) tepat di batas, belum MISSED.
        clock.set(Instant.parse("2026-11-13T14:00:00Z"));
        occurrenceService.markMissed();

        assertThat(statusByTime(setup.scheduleId())).containsExactly(
                LogStatus.TAKEN,       // J1: tidak disentuh walau sudah lewat
                LogStatus.MISSED,      // J2: lewat dari cutoff
                LogStatus.PENDING,     // J3: tepat di cutoff (perbandingan ketat)
                LogStatus.PENDING);    // J4: masih di masa depan
    }

    // ---------------- reaksi terhadap perubahan jadwal ----------------

    @Test
    void updatingTimes_reconciles_keepingUnchangedOccurrences() throws Exception {
        Setup setup = dailySetup();
        List<MedicationLog> before = logsOf(setup.scheduleId());
        UUID idJ2 = before.get(1).getId();
        UUID idJ4 = before.get(3).getId();

        // 20:00 diganti 12:00 (WIB); 08:00 tetap.
        api(setup.session(), patch("/api/schedules/" + setup.scheduleId()),
                "{\"times\":[{\"scheduledTime\":\"08:00:00\"},{\"scheduledTime\":\"12:00:00\"}]}")
                .andExpect(status().isOk());

        List<MedicationLog> after = logsOf(setup.scheduleId());
        assertThat(after).hasSize(6);

        assertThat(instantsWith(after, LogStatus.PENDING)).containsExactlyInAnyOrder(
                J2, J4, Instant.parse("2026-11-13T05:00:00Z"), Instant.parse("2026-11-14T05:00:00Z"));
        assertThat(instantsWith(after, LogStatus.CANCELLED)).containsExactlyInAnyOrder(J1, J3);

        // Occurrence 08:00 dipertahankan (id sama), bukan dibuat ulang.
        assertThat(after).filteredOn(log -> log.getScheduledAt().equals(J2) || log.getScheduledAt().equals(J4))
                .extracting(MedicationLog::getId).containsExactlyInAnyOrder(idJ2, idJ4);

        // Jam 20:00 terhapus -> FK ON DELETE SET NULL mengosongkan schedule_time_id log lama, riwayatnya tetap ada.
        assertThat(after).filteredOn(log -> log.getStatus() == LogStatus.CANCELLED)
                .allSatisfy(log -> assertThat(log.getScheduleTimeId()).isNull());
    }

    @Test
    void updatingDose_refreshesSnapshotOfPendingOccurrences_keepingIds() throws Exception {
        Setup setup = dailySetup();
        List<UUID> idsBefore = logsOf(setup.scheduleId()).stream().map(MedicationLog::getId).toList();

        api(setup.session(), patch("/api/schedules/" + setup.scheduleId()), "{\"doseQuantity\":2}")
                .andExpect(status().isOk());

        List<MedicationLog> after = logsOf(setup.scheduleId());
        assertThat(after).extracting(MedicationLog::getId).containsExactlyInAnyOrderElementsOf(idsBefore);
        assertThat(after).allSatisfy(log -> {
            assertThat(log.getStatus()).isEqualTo(LogStatus.PENDING);
            assertThat(log.getDoseQuantity()).isEqualByComparingTo("2");
        });
    }

    @Test
    void deletingSchedule_cancelsPendingOccurrences() throws Exception {
        Setup setup = dailySetup();

        api(setup.session(), delete("/api/schedules/" + setup.scheduleId()), null).andExpect(status().isNoContent());

        assertThat(logsOf(setup.scheduleId())).hasSize(4)
                .allSatisfy(log -> assertThat(log.getStatus()).isEqualTo(LogStatus.CANCELLED));
    }

    // ---------------- reaksi terhadap perubahan obat dan pengguna ----------------

    @Test
    void pausingMedication_cancels_andResumingRevivesTheSameOccurrences() throws Exception {
        Setup setup = dailySetup();
        List<UUID> idsBefore = logsOf(setup.scheduleId()).stream().map(MedicationLog::getId).toList();

        api(setup.session(), patch("/api/medications/" + setup.medicationId()), "{\"isActive\":false}")
                .andExpect(status().isOk());
        assertThat(logsOf(setup.scheduleId()))
                .allSatisfy(log -> assertThat(log.getStatus()).isEqualTo(LogStatus.CANCELLED));

        // Job tidak boleh menghidupkan occurrence milik obat yang sedang dijeda.
        batchService.generateForAllActive();
        assertThat(logsOf(setup.scheduleId()))
                .allSatisfy(log -> assertThat(log.getStatus()).isEqualTo(LogStatus.CANCELLED));

        api(setup.session(), patch("/api/medications/" + setup.medicationId()), "{\"isActive\":true}")
                .andExpect(status().isOk());

        List<MedicationLog> after = logsOf(setup.scheduleId());
        assertThat(after).extracting(MedicationLog::getId).containsExactlyInAnyOrderElementsOf(idsBefore);
        assertThat(after).allSatisfy(log -> assertThat(log.getStatus()).isEqualTo(LogStatus.PENDING));
    }

    @Test
    void deletingMedication_cancelsPendingOccurrences() throws Exception {
        Setup setup = dailySetup();

        api(setup.session(), delete("/api/medications/" + setup.medicationId()), null)
                .andExpect(status().isNoContent());

        assertThat(logsOf(setup.scheduleId())).hasSize(4)
                .allSatisfy(log -> assertThat(log.getStatus()).isEqualTo(LogStatus.CANCELLED));
    }

    @Test
    void changingTimezone_recomputesOccurrencesInTheNewZone() throws Exception {
        Setup setup = dailySetup();

        api(setup.session(), patch("/api/users/me"), "{\"timezone\":\"Asia/Makassar\"}")
                .andExpect(status().isOk());

        List<MedicationLog> after = logsOf(setup.scheduleId());
        assertThat(after).hasSize(8);
        // Jam dinding yang sama (08:00 dan 20:00) kini jatuh satu jam lebih awal dalam UTC (WITA = UTC+8).
        assertThat(instantsWith(after, LogStatus.PENDING)).containsExactlyInAnyOrder(
                Instant.parse("2026-11-12T12:00:00Z"), Instant.parse("2026-11-13T00:00:00Z"),
                Instant.parse("2026-11-13T12:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"));
        assertThat(instantsWith(after, LogStatus.CANCELLED)).containsExactlyInAnyOrder(J1, J2, J3, J4);
    }

    // ---------------- helper ----------------

    private record Setup(Session session, String medicationId, String scheduleId) {
    }

    private Setup dailySetup() throws Exception {
        Session session = login();
        String medicationId = createMedication(session);
        return new Setup(session, medicationId, createSchedule(session, medicationId, DAILY_BODY));
    }

    private Session login() throws Exception {
        return AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");
    }

    private String createMedication(Session session) throws Exception {
        String json = api(session, post("/api/medications"), "{\"name\":\"Paracetamol\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.id");
    }

    private String createSchedule(Session session, String medicationId, String body) throws Exception {
        String json = api(session, post("/api/medications/" + medicationId + "/schedules"), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.id");
    }

    private ResultActions api(Session session, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", session.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    /** Semua log sebuah jadwal (semua status), urut waktu. */
    private List<MedicationLog> logsOf(String scheduleId) {
        return logRepository.findByScheduleIdAndScheduledAtGreaterThanEqual(UUID.fromString(scheduleId), Instant.EPOCH)
                .stream()
                .sorted(Comparator.comparing(MedicationLog::getScheduledAt).thenComparing(MedicationLog::getId))
                .toList();
    }

    private List<LogStatus> statusByTime(String scheduleId) {
        return logsOf(scheduleId).stream().map(MedicationLog::getStatus).toList();
    }

    private static Set<Instant> instantsWith(List<MedicationLog> logs, LogStatus status) {
        return logs.stream().filter(log -> log.getStatus() == status)
                .map(MedicationLog::getScheduledAt).collect(java.util.stream.Collectors.toSet());
    }
}
