package com.interval.apibackend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Comparator;
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

import com.interval.apibackend.notification.push.PushMessage;
import com.interval.apibackend.notification.repository.DeviceRepository;
import com.interval.apibackend.notification.service.NotificationDispatchService;
import com.interval.apibackend.support.AuthTestHelper;
import com.interval.apibackend.support.AuthTestHelper.Session;
import com.interval.apibackend.support.FakePushSender;
import com.interval.apibackend.support.MutableClock;
import com.interval.apibackend.support.TestClockConfig;
import com.interval.apibackend.support.TestPushConfig;
import com.interval.apibackend.tracking.entity.MedicationLog;
import com.interval.apibackend.tracking.repository.MedicationLogRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Modul Notification end-to-end terhadap MySQL asli (butuh Docker), dengan jam dan pengirim push palsu.
 *
 * "Sekarang" = Kamis 12 Nov 2026 10:00 UTC = 17:00 WIB. Jadwal DAILY pukul 17:05 WIB menghasilkan occurrence
 * pertama pukul 10:05Z; pukul 17:30 WIB menghasilkan 10:30Z. Job terjadwal dimatikan: test memanggil
 * dispatcher langsung. Dispatcher memproses SEMUA pengguna di database, jadi test menyaring pesan
 * berdasarkan token perangkat miliknya.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import({TestClockConfig.class, TestPushConfig.class})
@TestPropertySource(properties = "interval.scheduling.enabled=false")
class NotificationFlowIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private FakePushSender pushSender;

    @Autowired
    private NotificationDispatchService dispatchService;

    @Autowired
    private MedicationLogRepository logRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @BeforeEach
    void reset() {
        clock.set(TestClockConfig.BASE);
        pushSender.reset();
    }

    // ---------------- autentikasi ----------------

    @Test
    void allEndpoints_requireAuthentication() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/notification-preferences")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notification-preferences").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/devices/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/history")).andExpect(status().isUnauthorized());
    }

    // ---------------- preferensi ----------------

    @Test
    void preferences_haveDefaults_andPartialUpdatesPersist() throws Exception {
        Session session = login();

        api(session, get("/api/notification-preferences"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification preferences retrieved successfully"))
                .andExpect(jsonPath("$.data.notificationsEnabled").value(true))
                .andExpect(jsonPath("$.data.soundEnabled").value(true))
                .andExpect(jsonPath("$.data.reminderBeforeMinutes").value(0));

        api(session, patch("/api/notification-preferences"), "{\"soundEnabled\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification preferences updated successfully"))
                .andExpect(jsonPath("$.data.notificationsEnabled").value(true))
                .andExpect(jsonPath("$.data.soundEnabled").value(false));

        api(session, patch("/api/notification-preferences"),
                "{\"notificationsEnabled\":false,\"reminderBeforeMinutes\":10}")
                .andExpect(jsonPath("$.data.notificationsEnabled").value(false))
                .andExpect(jsonPath("$.data.soundEnabled").value(false))
                .andExpect(jsonPath("$.data.reminderBeforeMinutes").value(10));

        api(session, patch("/api/notification-preferences"), "{}").andExpect(status().isOk());
        api(session, patch("/api/notification-preferences"), "{\"reminderBeforeMinutes\":1440}")
                .andExpect(jsonPath("$.data.reminderBeforeMinutes").value(1440));       // batas atas valid
        api(session, patch("/api/notification-preferences"), "{\"reminderBeforeMinutes\":0}")
                .andExpect(jsonPath("$.data.reminderBeforeMinutes").value(0));          // batas bawah valid

        api(session, get("/api/notification-preferences"), null)
                .andExpect(jsonPath("$.data.notificationsEnabled").value(false))
                .andExpect(jsonPath("$.data.soundEnabled").value(false));
    }

    @Test
    void preferences_withInvalidValues_returns422_andChangesNothing() throws Exception {
        Session session = login();

        api(session, patch("/api/notification-preferences"), "{\"reminderBeforeMinutes\":1441}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Invalid notification preferences"))
                .andExpect(jsonPath("$.errors.reminderBeforeMinutes").value("Value must be between 0 and 1440"));
        api(session, patch("/api/notification-preferences"), "{\"reminderBeforeMinutes\":-1}")
                .andExpect(status().isUnprocessableEntity());
        api(session, patch("/api/notification-preferences"), "{\"reminderBeforeMinutes\":\"abc\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.reminderBeforeMinutes").value("Invalid value"));

        api(session, patch("/api/notification-preferences"),
                "{\"soundEnabled\":false,\"reminderBeforeMinutes\":99999}")
                .andExpect(status().isUnprocessableEntity());
        api(session, get("/api/notification-preferences"), null)
                .andExpect(jsonPath("$.data.soundEnabled").value(true))          // tidak ikut berubah
                .andExpect(jsonPath("$.data.reminderBeforeMinutes").value(0));
    }

    // ---------------- perangkat ----------------

    @Test
    void devices_register_createsThenUpdatesTheSameDevice() throws Exception {
        Session session = login();
        String token = newToken();

        String id = JsonPath.read(registerDevice(session, token, "ANDROID", "Pixel 8")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Device registered successfully"))
                .andExpect(jsonPath("$.data.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.deviceName").value("Pixel 8"))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.fcmToken").doesNotExist())            // token adalah kredensial
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        registerDevice(session, token, "ANDROID", "Pixel 8 Pro")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Device updated successfully"))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.deviceName").value("Pixel 8 Pro"));

        // deviceName tidak dikirim -> nama lama dipertahankan
        api(session, post("/api/devices"), "{\"fcmToken\":\"" + token + "\",\"platform\":\"ANDROID\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceName").value("Pixel 8 Pro"));
    }

    @Test
    void devices_tokenMovesToTheLatestUser() throws Exception {
        Session alice = login();
        Session bob = login();
        String token = newToken();

        String id = JsonPath.read(registerDevice(alice, token, "IOS", "iPhone")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");

        // Perangkat yang sama dipakai akun lain: token berpindah, id tetap.
        registerDevice(bob, token, "IOS", "iPhone")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        api(alice, delete("/api/devices/" + id), null).andExpect(status().isNotFound());   // bukan miliknya lagi
        api(bob, delete("/api/devices/" + id), null).andExpect(status().isNoContent());
    }

    @Test
    void devices_withInvalidInput_returns422() throws Exception {
        Session session = login();

        api(session, post("/api/devices"), "{\"fcmToken\":\"  \",\"platform\":\"ANDROID\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.fcmToken").value("FCM token is required"));
        api(session, post("/api/devices"), "{\"fcmToken\":\"" + newToken() + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.platform").value("Platform is required"));
        api(session, post("/api/devices"), "{\"fcmToken\":\"" + newToken() + "\",\"platform\":\"SYMBIAN\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.platform").value("Value must be one of: ANDROID, IOS, WEB"));
        api(session, post("/api/devices"), "{\"fcmToken\":\"" + "x".repeat(513) + "\",\"platform\":\"WEB\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.fcmToken").value("FCM token must be at most 512 characters"));
        api(session, post("/api/devices"),
                "{\"fcmToken\":\"" + newToken() + "\",\"platform\":\"WEB\",\"deviceName\":\"" + "n".repeat(101) + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.deviceName").value("Device name must be at most 100 characters"));
    }

    @Test
    void devices_delete_andOwnership() throws Exception {
        Session alice = login();
        Session bob = login();
        String id = JsonPath.read(registerDevice(alice, newToken(), "ANDROID", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");

        api(bob, delete("/api/devices/" + id), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Device not found"));
        api(alice, delete("/api/devices/" + id), null).andExpect(status().isNoContent());
        api(alice, delete("/api/devices/" + id), null).andExpect(status().isNotFound());
        api(alice, delete("/api/devices/bukan-uuid"), null).andExpect(status().isBadRequest());
    }

    // ---------------- pengiriman ----------------

    @Test
    void dispatch_dueReminder_isSentOnceToEachActiveDevice_andRecordedInHistory() throws Exception {
        Session session = login();
        UUID logId = firstLogOf(session, "17:05:00");               // 10:05Z
        String tokenA = newToken();
        String tokenB = newToken();
        registerDevice(session, tokenA, "ANDROID", "A").andExpect(status().isCreated());
        registerDevice(session, tokenB, "IOS", "B").andExpect(status().isCreated());

        clock.set(Instant.parse("2026-11-12T10:05:30Z"));
        dispatchService.dispatchDue();

        assertThat(pushSender.sentTo(tokenA)).hasSize(1);
        assertThat(pushSender.sentTo(tokenB)).hasSize(1);
        PushMessage message = pushSender.sentTo(tokenA).get(0);
        assertThat(message.title()).isEqualTo("Waktunya minum obat");
        assertThat(message.body()).isEqualTo("Paracetamol, 1 tablet");
        assertThat(message.sound()).isTrue();
        assertThat(message.data()).containsEntry("medicationLogId", logId.toString())
                .containsEntry("type", "MEDICATION_REMINDER").containsEntry("sound", "true");

        // Dijalankan lagi: tidak ada kiriman ganda.
        dispatchService.dispatchDue();
        assertThat(pushSender.sentTo(tokenA)).hasSize(1);
        assertThat(pushSender.sentTo(tokenB)).hasSize(1);

        api(session, get("/api/notifications/history"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification history retrieved successfully"))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].medicationLogId").value(logId.toString()))
                .andExpect(jsonPath("$.data.items[0].notificationType").value("MEDICATION_REMINDER"))
                .andExpect(jsonPath("$.data.items[0].deliveryStatus").value("SENT"))
                .andExpect(jsonPath("$.data.items[0].sentAt").value("2026-11-12T17:05:30+07:00"))
                .andExpect(jsonPath("$.data.items[0].errorMessage").isEmpty())
                .andExpect(jsonPath("$.data.items[0].createdAt").value(endsWith("+07:00")))
                .andExpect(jsonPath("$.data.items[0].id").value(notNullValue()));
    }

    @Test
    void dispatch_respectsDisabledNotifications() throws Exception {
        Session session = login();
        firstLogOf(session, "17:05:00");
        String token = newToken();
        registerDevice(session, token, "ANDROID", "A").andExpect(status().isCreated());
        api(session, patch("/api/notification-preferences"), "{\"notificationsEnabled\":false}")
                .andExpect(status().isOk());

        clock.set(Instant.parse("2026-11-12T10:05:30Z"));
        dispatchService.dispatchDue();

        assertThat(pushSender.sentTo(token)).isEmpty();
    }

    @Test
    void dispatch_carriesTheSoundPreference() throws Exception {
        Session session = login();
        firstLogOf(session, "17:05:00");
        String token = newToken();
        registerDevice(session, token, "ANDROID", "A").andExpect(status().isCreated());
        api(session, patch("/api/notification-preferences"), "{\"soundEnabled\":false}").andExpect(status().isOk());

        clock.set(Instant.parse("2026-11-12T10:05:30Z"));
        dispatchService.dispatchDue();

        PushMessage message = pushSender.sentTo(token).get(0);
        assertThat(message.sound()).isFalse();
        assertThat(message.data()).containsEntry("sound", "false");
    }

    @Test
    void dispatch_preReminder_isSentBeforeTheDose_thenTheOnTimeReminder() throws Exception {
        Session session = login();
        firstLogOf(session, "17:30:00");                            // 10:30Z
        String token = newToken();
        registerDevice(session, token, "ANDROID", "A").andExpect(status().isCreated());
        api(session, patch("/api/notification-preferences"), "{\"reminderBeforeMinutes\":10}")
                .andExpect(status().isOk());

        clock.set(Instant.parse("2026-11-12T10:19:00Z"));           // 11 menit lagi: belum waktunya
        dispatchService.dispatchDue();
        assertThat(pushSender.sentTo(token)).isEmpty();

        clock.set(Instant.parse("2026-11-12T10:20:30Z"));           // 9,5 menit lagi
        dispatchService.dispatchDue();
        assertThat(pushSender.sentTo(token)).hasSize(1);
        assertThat(pushSender.sentTo(token).get(0).title()).isEqualTo("Sebentar lagi waktunya minum obat");
        assertThat(pushSender.sentTo(token).get(0).body()).isEqualTo("Paracetamol, 1 tablet dalam 10 menit");

        dispatchService.dispatchDue();                              // tidak diulang
        assertThat(pushSender.sentTo(token)).hasSize(1);

        clock.set(Instant.parse("2026-11-12T10:30:10Z"));           // waktunya
        dispatchService.dispatchDue();
        assertThat(pushSender.sentTo(token)).hasSize(2);
        assertThat(pushSender.sentTo(token).get(1).title()).isEqualTo("Waktunya minum obat");

        Session later = relogin(session);
        api(later, get("/api/notifications/history"), null)
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[*].notificationType",
                        containsInAnyOrder("PRE_REMINDER", "MEDICATION_REMINDER")));
    }

    @Test
    void dispatch_skipsOccurrencesThatAreNoLongerPending() throws Exception {
        Session session = login();
        UUID logId = firstLogOf(session, "17:05:00");
        String token = newToken();
        registerDevice(session, token, "ANDROID", "A").andExpect(status().isCreated());
        api(session, patch("/api/medication-logs/" + logId), "{\"status\":\"TAKEN\"}").andExpect(status().isOk());

        clock.set(Instant.parse("2026-11-12T10:05:30Z"));
        dispatchService.dispatchDue();

        assertThat(pushSender.sentTo(token)).isEmpty();
    }

    @Test
    void dispatch_doesNotSendStaleReminders() throws Exception {
        Session session = login();
        firstLogOf(session, "17:05:00");                            // 10:05Z
        String token = newToken();
        registerDevice(session, token, "ANDROID", "A").andExpect(status().isCreated());

        // 65 menit setelah jadwal (batas MISSED 60 menit): terlalu terlambat, walau job MISSED belum jalan.
        clock.set(Instant.parse("2026-11-12T11:10:00Z"));
        dispatchService.dispatchDue();

        assertThat(pushSender.sentTo(token)).isEmpty();
    }

    @Test
    void dispatch_invalidToken_deactivatesTheDevice_andReRegistrationReactivatesIt() throws Exception {
        Session session = login();
        firstLogOf(session, "17:05:00");
        String token = newToken();
        registerDevice(session, token, "ANDROID", "A").andExpect(status().isCreated());
        pushSender.invalidate(token);

        clock.set(Instant.parse("2026-11-12T10:05:30Z"));
        dispatchService.dispatchDue();

        assertThat(pushSender.sentTo(token)).hasSize(1);
        assertThat(deviceRepository.findByFcmToken(token)).hasValueSatisfying(
                device -> assertThat(device.isActive()).isFalse());
        api(session, get("/api/notifications/history"), null)
                .andExpect(jsonPath("$.data.items[0].deliveryStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].errorMessage").value("UNREGISTERED"));

        // Aplikasi mendaftar ulang token yang sama: aktif lagi.
        registerDevice(session, token, "ANDROID", "A")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    void dispatch_failure_isRecorded_andNotRetriedAutomatically() throws Exception {
        Session session = login();
        firstLogOf(session, "17:05:00");
        String token = newToken();
        registerDevice(session, token, "ANDROID", "A").andExpect(status().isCreated());
        pushSender.fail(token, "FCM unavailable");

        clock.set(Instant.parse("2026-11-12T10:05:30Z"));
        dispatchService.dispatchDue();
        dispatchService.dispatchDue();

        assertThat(pushSender.sentTo(token)).hasSize(1);            // percobaan gagal tidak diulang
        assertThat(deviceRepository.findByFcmToken(token)).hasValueSatisfying(
                device -> assertThat(device.isActive()).isTrue());   // gagal biasa tidak menonaktifkan perangkat
        api(session, get("/api/notifications/history"), null)
                .andExpect(jsonPath("$.data.items[0].deliveryStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].errorMessage").value("FCM unavailable"))
                .andExpect(jsonPath("$.data.items[0].sentAt").isEmpty());
    }

    // ---------------- riwayat ----------------

    @Test
    void history_isFilteredPaginatedAndIsolatedPerUser() throws Exception {
        Session alice = login();
        Session bob = login();
        firstLogOf(alice, "17:05:00");
        String failingToken = newToken();
        String okToken = newToken();
        registerDevice(alice, failingToken, "ANDROID", "A").andExpect(status().isCreated());
        registerDevice(alice, okToken, "IOS", "B").andExpect(status().isCreated());
        pushSender.fail(failingToken, "boom");

        clock.set(Instant.parse("2026-11-12T10:05:30Z"));
        dispatchService.dispatchDue();

        api(alice, get("/api/notifications/history"), null).andExpect(jsonPath("$.data.totalItems").value(2));
        api(alice, get("/api/notifications/history?deliveryStatus=FAILED"), null)
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].errorMessage").value("boom"));
        api(alice, get("/api/notifications/history?deliveryStatus=SENT"), null)
                .andExpect(jsonPath("$.data.totalItems").value(1));
        api(alice, get("/api/notifications/history?size=1&page=1"), null)
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1));

        api(bob, get("/api/notifications/history"), null).andExpect(jsonPath("$.data.totalItems").value(0));

        api(alice, get("/api/notifications/history?deliveryStatus=DONE"), null).andExpect(status().isBadRequest());
        api(alice, get("/api/notifications/history?size=101"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.size").value("Size must be between 1 and 100"));
    }

    // ---------------- helper ----------------

    private Session login() throws Exception {
        return AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");
    }

    private Session relogin(Session session) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + session.email() + "\",\"password\":\""
                                + AuthTestHelper.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Session(session.email(), JsonPath.read(json, "$.data.accessToken"),
                JsonPath.read(json, "$.data.refreshToken"));
    }

    private static String newToken() {
        return "fcm-token-" + UUID.randomUUID();
    }

    private ResultActions registerDevice(Session session, String token, String platform, String name) throws Exception {
        String nameJson = name == null ? "" : ",\"deviceName\":\"" + name + "\"";
        return api(session, post("/api/devices"),
                "{\"fcmToken\":\"" + token + "\",\"platform\":\"" + platform + "\"" + nameJson + "}");
    }

    /** Membuat obat + jadwal DAILY (mulai 12 Nov) pada jam lokal tertentu; mengembalikan id log pertamanya. */
    private UUID firstLogOf(Session session, String localTime) throws Exception {
        String medication = api(session, post("/api/medications"), "{\"name\":\"Paracetamol\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String medicationId = JsonPath.read(medication, "$.data.id");

        String schedule = api(session, post("/api/medications/" + medicationId + "/schedules"),
                "{\"frequencyType\":\"DAILY\",\"doseQuantity\":1,\"doseUnit\":\"TABLET\","
                        + "\"startDate\":\"2026-11-12\",\"times\":[{\"scheduledTime\":\"" + localTime + "\"}]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String scheduleId = JsonPath.read(schedule, "$.data.id");

        return logRepository.findByScheduleIdAndScheduledAtGreaterThanEqual(UUID.fromString(scheduleId), Instant.EPOCH)
                .stream()
                .min(Comparator.comparing(MedicationLog::getScheduledAt))
                .orElseThrow()
                .getId();
    }

    private ResultActions api(Session session, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", session.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }
}
