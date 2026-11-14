package com.interval.apibackend.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
import com.interval.apibackend.support.TestClockConfig;
import com.interval.apibackend.tracking.entity.MedicationLog;
import com.interval.apibackend.tracking.repository.MedicationLogRepository;
import com.interval.apibackend.tracking.service.OccurrenceService;
import com.jayway.jsonpath.JsonPath;

/**
 * Modul Tracking end-to-end terhadap MySQL asli (butuh Docker), dengan jam yang dikendalikan test.
 *
 * "Sekarang" = Kamis 12 Nov 2026 10:00 UTC = 17:00 WIB. Jadwal DAILY 08:00 dan 20:00 WIB (mulai 12 Nov)
 * menghasilkan empat occurrence (lihat OccurrenceIntegrationTest):
 *   J1 = Kamis 20:00 WIB, J2 = Jumat 08:00 WIB, J3 = Jumat 20:00 WIB, J4 = Sabtu 08:00 WIB.
 * Rentang default berakhir HARI INI, jadi hanya J1 yang terlihat tanpa parameter "to".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestClockConfig.class)
@TestPropertySource(properties = "interval.scheduling.enabled=false")
class TrackingFlowIntegrationTest {

    private static final String DAILY_BODY = """
            {"frequencyType":"DAILY","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-12",
             "times":[{"scheduledTime":"08:00:00"},{"scheduledTime":"20:00:00"}]}""";

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
    private OccurrenceService occurrenceService;

    @BeforeEach
    void resetClock() {
        clock.set(TestClockConfig.BASE);
    }

    // ---------------- autentikasi ----------------

    @Test
    void allEndpoints_requireAuthentication() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/medication-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/medication-logs/adherence")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/medication-logs/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/medication-logs/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"TAKEN\"}")).andExpect(status().isUnauthorized());
    }

    // ---------------- list ----------------

    @Test
    void list_defaultRangeEndsToday_andToIncludesUpcomingOccurrences() throws Exception {
        Setup setup = dailySetup();

        api(setup.session(), get("/api/medication-logs"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Medication logs retrieved successfully"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].scheduledAt").value("2026-11-12T20:00:00+07:00"));

        api(setup.session(), get("/api/medication-logs?to=2026-11-14"), null)
                .andExpect(jsonPath("$.data.totalItems").value(4))
                .andExpect(jsonPath("$.data.items[0].scheduledAt").value("2026-11-14T08:00:00+07:00"))   // terbaru dulu
                .andExpect(jsonPath("$.data.items[3].scheduledAt").value("2026-11-12T20:00:00+07:00"))
                .andExpect(jsonPath("$.data.items[0].id").value(notNullValue()))
                .andExpect(jsonPath("$.data.items[0].medicationId").value(setup.medicationId()))
                .andExpect(jsonPath("$.data.items[0].medicationName").value("Paracetamol"))
                .andExpect(jsonPath("$.data.items[0].scheduleId").value(setup.scheduleId()))
                .andExpect(jsonPath("$.data.items[0].scheduleTimeId").value(notNullValue()))
                .andExpect(jsonPath("$.data.items[0].doseQuantity").value(1.0))
                .andExpect(jsonPath("$.data.items[0].doseUnit").value("TABLET"))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].actionAt").isEmpty())
                .andExpect(jsonPath("$.data.items[0].notes").isEmpty());
    }

    @Test
    void list_filtersByLocalDay_status_andMedication() throws Exception {
        Setup setup = dailySetup();
        List<UUID> ids = logIds(setup.scheduleId());

        // hari lokal 13 Nov: Jumat 08:00 dan 20:00 WIB
        api(setup.session(), get("/api/medication-logs?from=2026-11-13&to=2026-11-13"), null)
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].scheduledAt").value("2026-11-13T20:00:00+07:00"))
                .andExpect(jsonPath("$.data.items[1].scheduledAt").value("2026-11-13T08:00:00+07:00"));

        patchLog(setup.session(), ids.get(1), "{\"status\":\"TAKEN\"}").andExpect(status().isOk());
        api(setup.session(), get("/api/medication-logs?to=2026-11-14&status=TAKEN"), null)
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(ids.get(1).toString()));
        api(setup.session(), get("/api/medication-logs?to=2026-11-14&status=PENDING"), null)
                .andExpect(jsonPath("$.data.totalItems").value(3));

        // obat kedua dalam akun yang sama
        String secondMedication = createMedication(setup.session(), "Vitamin C");
        createSchedule(setup.session(), secondMedication, DAILY_BODY);
        api(setup.session(), get("/api/medication-logs?to=2026-11-14"), null)
                .andExpect(jsonPath("$.data.totalItems").value(8));
        api(setup.session(), get("/api/medication-logs?to=2026-11-14&medicationId=" + setup.medicationId()), null)
                .andExpect(jsonPath("$.data.totalItems").value(4));
    }

    @Test
    void list_isPaginated() throws Exception {
        Setup setup = dailySetup();

        api(setup.session(), get("/api/medication-logs?to=2026-11-14&size=3"), null)
                .andExpect(jsonPath("$.data.totalItems").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(3));
        api(setup.session(), get("/api/medication-logs?to=2026-11-14&size=3&page=1"), null)
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void list_withInvalidParameters() throws Exception {
        Session session = login();

        api(session, get("/api/medication-logs?from=2026-11-14&to=2026-11-13"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Invalid date range"))
                .andExpect(jsonPath("$.errors.to").value("End date must not be earlier than start date"));
        api(session, get("/api/medication-logs?from=2024-01-01&to=2026-11-14"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.to").value("Date range must not exceed 366 days"));
        api(session, get("/api/medication-logs?size=101"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.size").value("Size must be between 1 and 100"));
        api(session, get("/api/medication-logs?page=-1"), null).andExpect(status().isUnprocessableEntity());

        // tipe salah -> 400
        api(session, get("/api/medication-logs?from=bukan-tanggal"), null).andExpect(status().isBadRequest());
        api(session, get("/api/medication-logs?status=DONE"), null).andExpect(status().isBadRequest());
        api(session, get("/api/medication-logs?medicationId=abc"), null).andExpect(status().isBadRequest());
    }

    // ---------------- detail & kepemilikan ----------------

    @Test
    void detail_andOwnership() throws Exception {
        Setup alice = dailySetup();
        Session bob = login();
        UUID logId = logIds(alice.scheduleId()).get(0);

        api(alice.session(), get("/api/medication-logs/" + logId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(logId.toString()))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-11-12T20:00:00+07:00"));

        api(bob, get("/api/medication-logs/" + logId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Medication log not found"));
        patchLog(bob, logId, "{\"status\":\"TAKEN\"}").andExpect(status().isNotFound());
        api(bob, get("/api/medication-logs?to=2026-11-14"), null).andExpect(jsonPath("$.data.totalItems").value(0));

        // milik Alice tidak berubah
        api(alice.session(), get("/api/medication-logs/" + logId), null)
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        api(alice.session(), get("/api/medication-logs/" + UUID.randomUUID()), null).andExpect(status().isNotFound());
        api(alice.session(), get("/api/medication-logs/bukan-uuid"), null).andExpect(status().isBadRequest());
    }

    // ---------------- PATCH status ----------------

    @Test
    void patch_taken_setsActionAtInUserTimezone_andNotesFollowPresenceRules() throws Exception {
        Setup setup = dailySetup();
        UUID logId = logIds(setup.scheduleId()).get(0);

        patchLog(setup.session(), logId, "{\"status\":\"TAKEN\",\"notes\":\"  Sudah makan  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Medication log updated successfully"))
                .andExpect(jsonPath("$.data.status").value("TAKEN"))
                .andExpect(jsonPath("$.data.actionAt").value("2026-11-12T17:00:00+07:00"))   // sekarang, dalam WIB
                .andExpect(jsonPath("$.data.notes").value("Sudah makan"));

        // notes tidak dikirim -> tetap
        patchLog(setup.session(), logId, "{\"status\":\"TAKEN\"}")
                .andExpect(jsonPath("$.data.notes").value("Sudah makan"));
        // notes null -> dikosongkan; spasi saja -> null
        patchLog(setup.session(), logId, "{\"status\":\"TAKEN\",\"notes\":null}")
                .andExpect(jsonPath("$.data.notes").isEmpty());
        patchLog(setup.session(), logId, "{\"status\":\"TAKEN\",\"notes\":\"x\"}").andExpect(status().isOk());
        patchLog(setup.session(), logId, "{\"status\":\"TAKEN\",\"notes\":\"   \"}")
                .andExpect(jsonPath("$.data.notes").isEmpty());
    }

    @Test
    void patch_sameStatus_isIdempotent_andKeepsOriginalActionAt() throws Exception {
        Setup setup = dailySetup();
        UUID logId = logIds(setup.scheduleId()).get(0);
        patchLog(setup.session(), logId, "{\"status\":\"TAKEN\"}").andExpect(status().isOk());

        clock.set(Instant.parse("2026-11-12T10:05:00Z"));
        Session later = relogin(setup.session());
        patchLog(later, logId, "{\"status\":\"TAKEN\",\"notes\":\"retry\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionAt").value("2026-11-12T17:00:00+07:00"))   // bukan 17:05
                .andExpect(jsonPath("$.data.notes").value("retry"));
    }

    @Test
    void patch_allowedTransitions_betweenPendingTakenAndSkipped() throws Exception {
        Setup setup = dailySetup();
        UUID logId = logIds(setup.scheduleId()).get(0);

        patchLog(setup.session(), logId, "{\"status\":\"SKIPPED\"}")
                .andExpect(jsonPath("$.data.status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.actionAt").value(notNullValue()));
        patchLog(setup.session(), logId, "{\"status\":\"TAKEN\"}").andExpect(jsonPath("$.data.status").value("TAKEN"));
        patchLog(setup.session(), logId, "{\"status\":\"SKIPPED\"}").andExpect(jsonPath("$.data.status").value("SKIPPED"));
        patchLog(setup.session(), logId, "{\"status\":\"PENDING\"}")
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.actionAt").isEmpty());
    }

    @Test
    void patch_revertToPending_onlyWithinTheMissedThreshold() throws Exception {
        Setup setup = dailySetup();
        UUID j1 = logIds(setup.scheduleId()).get(0);                   // 13:00Z
        patchLog(setup.session(), j1, "{\"status\":\"TAKEN\"}").andExpect(status().isOk());

        // 30 menit setelah jadwal: masih boleh dibatalkan
        clock.set(Instant.parse("2026-11-12T13:30:00Z"));
        Session inTime = relogin(setup.session());
        patchLog(inTime, j1, "{\"status\":\"PENDING\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        patchLog(inTime, j1, "{\"status\":\"TAKEN\"}").andExpect(status().isOk());

        // 70 menit setelah jadwal: sudah melewati batas 60 menit
        clock.set(Instant.parse("2026-11-12T14:10:00Z"));
        Session tooLate = relogin(inTime);
        patchLog(tooLate, j1, "{\"status\":\"PENDING\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Invalid status transition"))
                .andExpect(jsonPath("$.errors.status")
                        .value("Cannot revert to PENDING after the missed threshold has passed"));
    }

    @Test
    void patch_missedLog_canBeTakenLate_butNotRevertedToPending() throws Exception {
        Setup setup = dailySetup();
        UUID j1 = logIds(setup.scheduleId()).get(0);                   // 13:00Z

        clock.set(Instant.parse("2026-11-12T14:30:00Z"));              // 90 menit kemudian
        occurrenceService.markMissed();
        Session later = relogin(setup.session());

        api(later, get("/api/medication-logs/" + j1), null).andExpect(jsonPath("$.data.status").value("MISSED"));

        patchLog(later, j1, "{\"status\":\"PENDING\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.status").value("Cannot change status from MISSED to PENDING"));
        patchLog(later, j1, "{\"status\":\"TAKEN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TAKEN"))
                .andExpect(jsonPath("$.data.actionAt").value("2026-11-12T21:30:00+07:00"));
    }

    @Test
    void patch_cancelledLog_isImmutable() throws Exception {
        Setup setup = dailySetup();
        UUID logId = logIds(setup.scheduleId()).get(0);
        api(setup.session(), delete("/api/schedules/" + setup.scheduleId()), null).andExpect(status().isNoContent());

        patchLog(setup.session(), logId, "{\"status\":\"TAKEN\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.status").value("Cannot change status from CANCELLED to TAKEN"));
    }

    @Test
    void patch_withInvalidInput_returns422() throws Exception {
        Setup setup = dailySetup();
        UUID logId = logIds(setup.scheduleId()).get(0);

        patchLog(setup.session(), logId, "{\"status\":\"MISSED\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.status").value("Status MISSED cannot be set manually"));
        patchLog(setup.session(), logId, "{\"status\":\"CANCELLED\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.status").value("Status CANCELLED cannot be set manually"));
        patchLog(setup.session(), logId, "{\"status\":\"DONE\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.status")
                        .value("Value must be one of: PENDING, TAKEN, SKIPPED, MISSED, CANCELLED"));
        patchLog(setup.session(), logId, "{\"notes\":\"tanpa status\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.status").value("Status is required"));
        patchLog(setup.session(), logId, "{\"status\":\"TAKEN\",\"notes\":\"" + "x".repeat(5001) + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.notes").value("Notes must be at most 5000 characters"));

        api(setup.session(), get("/api/medication-logs/" + logId), null)
                .andExpect(jsonPath("$.data.status").value("PENDING"));   // tidak ada yang berubah
    }

    // ---------------- adherence ----------------

    @Test
    void adherence_countsStatuses_andUsesTheResolvedOnesAsDenominator() throws Exception {
        Setup setup = dailySetup();
        List<UUID> ids = logIds(setup.scheduleId());
        patchLog(setup.session(), ids.get(0), "{\"status\":\"TAKEN\"}").andExpect(status().isOk());
        patchLog(setup.session(), ids.get(1), "{\"status\":\"SKIPPED\"}").andExpect(status().isOk());
        patchLog(setup.session(), ids.get(2), "{\"status\":\"TAKEN\"}").andExpect(status().isOk());

        api(setup.session(), get("/api/medication-logs/adherence?to=2026-11-14"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Medication adherence retrieved successfully"))
                .andExpect(jsonPath("$.data.totalScheduled").value(4))
                .andExpect(jsonPath("$.data.taken").value(2))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.missed").value(0))
                .andExpect(jsonPath("$.data.pending").value(1))
                .andExpect(jsonPath("$.data.adherencePercentage").value(66.67));   // 2 / (2+1+0)

        // J4 (Sabtu 08:00 WIB = 01:00Z) terlewat -> MISSED
        clock.set(Instant.parse("2026-11-14T02:30:00Z"));
        occurrenceService.markMissed();
        Session later = relogin(setup.session());

        api(later, get("/api/medication-logs/adherence?to=2026-11-14"), null)
                .andExpect(jsonPath("$.data.missed").value(1))
                .andExpect(jsonPath("$.data.pending").value(0))
                .andExpect(jsonPath("$.data.totalScheduled").value(4))
                .andExpect(jsonPath("$.data.adherencePercentage").value(50.0));   // 2 / (2+1+1)
    }

    @Test
    void adherence_isNullWhenNothingIsResolved_andCancelledAreNotCounted() throws Exception {
        Setup setup = dailySetup();

        api(setup.session(), get("/api/medication-logs/adherence?to=2026-11-14"), null)
                .andExpect(jsonPath("$.data.totalScheduled").value(4))
                .andExpect(jsonPath("$.data.pending").value(4))
                .andExpect(jsonPath("$.data.adherencePercentage").isEmpty());

        api(setup.session(), delete("/api/schedules/" + setup.scheduleId()), null).andExpect(status().isNoContent());

        api(setup.session(), get("/api/medication-logs/adherence?to=2026-11-14"), null)
                .andExpect(jsonPath("$.data.totalScheduled").value(0))
                .andExpect(jsonPath("$.data.pending").value(0))
                .andExpect(jsonPath("$.data.adherencePercentage").isEmpty());
    }

    @Test
    void adherence_filtersByMedication_andValidatesTheRange() throws Exception {
        Setup first = dailySetup();
        String secondMedication = createMedication(first.session(), "Vitamin C");
        createSchedule(first.session(), secondMedication, DAILY_BODY);
        patchLog(first.session(), logIds(first.scheduleId()).get(0), "{\"status\":\"TAKEN\"}")
                .andExpect(status().isOk());

        api(first.session(), get("/api/medication-logs/adherence?to=2026-11-14&medicationId=" + first.medicationId()), null)
                .andExpect(jsonPath("$.data.taken").value(1))
                .andExpect(jsonPath("$.data.pending").value(3))
                .andExpect(jsonPath("$.data.adherencePercentage").value(100.0));
        api(first.session(), get("/api/medication-logs/adherence?to=2026-11-14&medicationId=" + secondMedication), null)
                .andExpect(jsonPath("$.data.taken").value(0))
                .andExpect(jsonPath("$.data.pending").value(4))
                .andExpect(jsonPath("$.data.adherencePercentage").isEmpty());

        // tanpa parameter: 30 hari terakhir sampai hari ini (hanya J1 yang sudah masuk hari ini)
        api(first.session(), get("/api/medication-logs/adherence"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScheduled").value(2));

        api(first.session(), get("/api/medication-logs/adherence?from=2026-11-14&to=2026-11-13"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Invalid date range"))
                .andExpect(jsonPath("$.errors.to").value("End date must not be earlier than start date"));
    }

    // ---------------- helper ----------------

    private record Setup(Session session, String medicationId, String scheduleId) {
    }

    private Setup dailySetup() throws Exception {
        Session session = login();
        String medicationId = createMedication(session, "Paracetamol");
        return new Setup(session, medicationId, createSchedule(session, medicationId, DAILY_BODY));
    }

    private Session login() throws Exception {
        return AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");
    }

    /** Login ulang pada jam yang sekarang (token lama kedaluwarsa setelah jam dimajukan). */
    private Session relogin(Session session) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + session.email() + "\",\"password\":\""
                                + AuthTestHelper.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Session(session.email(), JsonPath.read(json, "$.data.accessToken"),
                JsonPath.read(json, "$.data.refreshToken"));
    }

    private String createMedication(Session session, String name) throws Exception {
        String json = api(session, post("/api/medications"), "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.id");
    }

    private String createSchedule(Session session, String medicationId, String body) throws Exception {
        String json = api(session, post("/api/medications/" + medicationId + "/schedules"), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.id");
    }

    private ResultActions patchLog(Session session, UUID logId, String body) throws Exception {
        return api(session, patch("/api/medication-logs/" + logId), body);
    }

    private ResultActions api(Session session, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", session.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    /** Id log sebuah jadwal, urut waktu (J1..J4). */
    private List<UUID> logIds(String scheduleId) {
        return logRepository.findByScheduleIdAndScheduledAtGreaterThanEqual(UUID.fromString(scheduleId), Instant.EPOCH)
                .stream()
                .sorted(Comparator.comparing(MedicationLog::getScheduledAt))
                .map(MedicationLog::getId)
                .toList();
    }
}
