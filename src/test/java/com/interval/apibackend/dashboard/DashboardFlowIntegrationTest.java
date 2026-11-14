package com.interval.apibackend.dashboard;

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
 * Dashboard end-to-end terhadap MySQL asli (butuh Docker), dengan jam yang dikendalikan test.
 *
 * "Sekarang" = Kamis 12 Nov 2026 10:00 UTC = 17:00 WIB. Jadwal DAILY pukul 18:00, 19:00, 20:00, 21:00 WIB
 * (mulai 12 Nov) menghasilkan 4 occurrence HARI INI (T1..T4 = 11:00Z, 12:00Z, 13:00Z, 14:00Z) dan 4 occurrence
 * BESOK (13 Nov, di dalam horizon 48 jam).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestClockConfig.class)
@TestPropertySource(properties = "interval.scheduling.enabled=false")
class DashboardFlowIntegrationTest {

    private static final String EVENING_SCHEDULE = """
            {"frequencyType":"DAILY","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-12",
             "times":[{"scheduledTime":"18:00:00"},{"scheduledTime":"19:00:00"},
                      {"scheduledTime":"20:00:00"},{"scheduledTime":"21:00:00"}]}""";

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

    // ---------------- autentikasi & isolasi ----------------

    @Test
    void allEndpoints_requireAuthentication() throws Exception {
        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/today")).andExpect(status().isUnauthorized());
    }

    @Test
    void newUser_seesAnEmptyDashboard() throws Exception {
        Session session = login();

        api(session, get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Dashboard summary retrieved successfully"))
                .andExpect(jsonPath("$.data.activeMedicationCount").value(0))
                .andExpect(jsonPath("$.data.todayScheduledCount").value(0))
                .andExpect(jsonPath("$.data.todayTakenCount").value(0))
                .andExpect(jsonPath("$.data.todaySkippedCount").value(0))
                .andExpect(jsonPath("$.data.todayPendingCount").value(0))
                .andExpect(jsonPath("$.data.todayMissedCount").value(0))
                .andExpect(jsonPath("$.data.weeklyAdherencePercentage").isEmpty());

        api(session, get("/api/dashboard/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Today's schedule retrieved successfully"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void dashboards_areIsolatedPerUser() throws Exception {
        eveningSetup();                       // data milik pengguna lain
        Session bob = login();

        api(bob, get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.data.activeMedicationCount").value(0))
                .andExpect(jsonPath("$.data.todayScheduledCount").value(0));
        api(bob, get("/api/dashboard/today")).andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---------------- summary ----------------

    @Test
    void summary_countsTodayByStatus_andComputesWeeklyAdherence() throws Exception {
        Setup setup = eveningSetup();
        List<UUID> ids = logIds(setup.scheduleId());       // T1..T4 hari ini, lalu 4 log besok

        api(setup.session(), get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.data.activeMedicationCount").value(1))
                .andExpect(jsonPath("$.data.todayScheduledCount").value(4))     // log besok tidak ikut
                .andExpect(jsonPath("$.data.todayPendingCount").value(4))
                .andExpect(jsonPath("$.data.todayTakenCount").value(0))
                .andExpect(jsonPath("$.data.weeklyAdherencePercentage").isEmpty());

        patchLog(setup.session(), ids.get(0), "{\"status\":\"TAKEN\"}").andExpect(status().isOk());
        patchLog(setup.session(), ids.get(1), "{\"status\":\"SKIPPED\"}").andExpect(status().isOk());

        api(setup.session(), get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.data.todayScheduledCount").value(4))
                .andExpect(jsonPath("$.data.todayTakenCount").value(1))
                .andExpect(jsonPath("$.data.todaySkippedCount").value(1))
                .andExpect(jsonPath("$.data.todayPendingCount").value(2))
                .andExpect(jsonPath("$.data.todayMissedCount").value(0))
                .andExpect(jsonPath("$.data.weeklyAdherencePercentage").value(50.0));   // 1 / (1+1+0)

        // 21:30 WIB: T3 (20:00) terlewat 60 menit -> MISSED; T4 (21:00) belum
        clock.set(Instant.parse("2026-11-12T14:30:00Z"));
        occurrenceService.markMissed();
        Session later = relogin(setup.session());

        api(later, get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.data.todayTakenCount").value(1))
                .andExpect(jsonPath("$.data.todaySkippedCount").value(1))
                .andExpect(jsonPath("$.data.todayMissedCount").value(1))
                .andExpect(jsonPath("$.data.todayPendingCount").value(1))
                .andExpect(jsonPath("$.data.todayScheduledCount").value(4))
                .andExpect(jsonPath("$.data.weeklyAdherencePercentage").value(33.33));   // 1 / (1+1+1)
    }

    @Test
    void summary_excludesCancelledOccurrences_andCountsOnlyActiveMedications() throws Exception {
        Setup setup = eveningSetup();

        api(setup.session(), delete("/api/schedules/" + setup.scheduleId())).andExpect(status().isNoContent());
        api(setup.session(), get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.data.todayScheduledCount").value(0))
                .andExpect(jsonPath("$.data.todayPendingCount").value(0))
                .andExpect(jsonPath("$.data.weeklyAdherencePercentage").isEmpty())
                .andExpect(jsonPath("$.data.activeMedicationCount").value(1));    // obatnya masih aktif

        api(setup.session(), patch("/api/medications/" + setup.medicationId()), "{\"isActive\":false}")
                .andExpect(status().isOk());
        api(setup.session(), get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.data.activeMedicationCount").value(0));    // dijeda tidak dihitung

        api(setup.session(), patch("/api/medications/" + setup.medicationId()), "{\"isActive\":true}")
                .andExpect(status().isOk());
        api(setup.session(), get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.data.activeMedicationCount").value(1));

        api(setup.session(), delete("/api/medications/" + setup.medicationId())).andExpect(status().isNoContent());
        api(setup.session(), get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.data.activeMedicationCount").value(0));    // dihapus tidak dihitung
    }

    // ---------------- today ----------------

    @Test
    void today_listsOnlyTodaysOccurrences_inTimeOrder_withMedicationDetails() throws Exception {
        Setup setup = eveningSetup();

        api(setup.session(), get("/api/dashboard/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))                  // 4 log besok tidak ikut
                .andExpect(jsonPath("$.data[0].scheduledAt").value("2026-11-12T18:00:00+07:00"))
                .andExpect(jsonPath("$.data[1].scheduledAt").value("2026-11-12T19:00:00+07:00"))
                .andExpect(jsonPath("$.data[2].scheduledAt").value("2026-11-12T20:00:00+07:00"))
                .andExpect(jsonPath("$.data[3].scheduledAt").value("2026-11-12T21:00:00+07:00"))
                .andExpect(jsonPath("$.data[0].logId").value(notNullValue()))
                .andExpect(jsonPath("$.data[0].medicationId").value(setup.medicationId()))
                .andExpect(jsonPath("$.data[0].medicationName").value("Paracetamol"))
                .andExpect(jsonPath("$.data[0].dosage").value("500 mg"))
                .andExpect(jsonPath("$.data[0].doseQuantity").value(1.0))
                .andExpect(jsonPath("$.data[0].doseUnit").value("TABLET"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].actionAt").isEmpty())
                .andExpect(jsonPath("$.data[0].instructions").value("Diminum setelah makan"));
    }

    @Test
    void today_reflectsActions_andExcludesCancelledOccurrences() throws Exception {
        Setup setup = eveningSetup();
        UUID t1 = logIds(setup.scheduleId()).get(0);
        patchLog(setup.session(), t1, "{\"status\":\"TAKEN\"}").andExpect(status().isOk());

        api(setup.session(), get("/api/dashboard/today"))
                .andExpect(jsonPath("$.data[0].logId").value(t1.toString()))
                .andExpect(jsonPath("$.data[0].status").value("TAKEN"))
                .andExpect(jsonPath("$.data[0].actionAt").value("2026-11-12T17:00:00+07:00"));

        // Obat dihapus: T2..T4 dibatalkan dan hilang dari daftar; T1 (sudah diminum) tetap sebagai riwayat hari ini.
        api(setup.session(), delete("/api/medications/" + setup.medicationId())).andExpect(status().isNoContent());

        api(setup.session(), get("/api/dashboard/today"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].logId").value(t1.toString()))
                .andExpect(jsonPath("$.data[0].status").value("TAKEN"))
                .andExpect(jsonPath("$.data[0].dosage").value("500 mg"));       // barisnya masih ada (soft delete)
    }

    @Test
    void today_followsTheLocalDateBoundary() throws Exception {
        Setup setup = eveningSetup();

        // 17:30 UTC tanggal 12 Nov = 00:30 WIB tanggal 13 Nov: "hari ini" berganti ke 13 Nov.
        clock.set(Instant.parse("2026-11-12T17:30:00Z"));
        Session afterMidnight = relogin(setup.session());

        api(afterMidnight, get("/api/dashboard/today"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].scheduledAt").value("2026-11-13T18:00:00+07:00"))
                .andExpect(jsonPath("$.data[3].scheduledAt").value("2026-11-13T21:00:00+07:00"));
        api(afterMidnight, get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.data.todayScheduledCount").value(4))
                .andExpect(jsonPath("$.data.todayPendingCount").value(4));
    }

    // ---------------- helper ----------------

    private record Setup(Session session, String medicationId, String scheduleId) {
    }

    private Setup eveningSetup() throws Exception {
        Session session = login();
        String medicationId = createMedication(session);
        return new Setup(session, medicationId, createSchedule(session, medicationId));
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

    private String createMedication(Session session) throws Exception {
        String json = api(session, post("/api/medications"),
                "{\"name\":\"Paracetamol\",\"dosage\":\"500 mg\",\"instructions\":\"Diminum setelah makan\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.id");
    }

    private String createSchedule(Session session, String medicationId) throws Exception {
        String json = api(session, post("/api/medications/" + medicationId + "/schedules"), EVENING_SCHEDULE)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.id");
    }

    private ResultActions patchLog(Session session, UUID logId, String body) throws Exception {
        return api(session, patch("/api/medication-logs/" + logId), body);
    }

    private ResultActions api(Session session, MockHttpServletRequestBuilder request) throws Exception {
        return api(session, request, null);
    }

    private ResultActions api(Session session, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", session.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    /** Id log sebuah jadwal, urut waktu: 4 log hari ini, lalu 4 log besok. */
    private List<UUID> logIds(String scheduleId) {
        List<UUID> ids = logRepository
                .findByScheduleIdAndScheduledAtGreaterThanEqual(UUID.fromString(scheduleId), Instant.EPOCH).stream()
                .sorted(Comparator.comparing(MedicationLog::getScheduledAt))
                .map(MedicationLog::getId)
                .toList();
        assertThat(ids).hasSize(8);
        return ids;
    }
}
