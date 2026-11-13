package com.interval.apibackend.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.interval.apibackend.schedule.event.ScheduleChangedEvent;
import com.interval.apibackend.schedule.event.ScheduleChangedEvent.Type;
import com.interval.apibackend.schedule.repository.MedicationScheduleRepository;
import com.interval.apibackend.support.AuthTestHelper;
import com.interval.apibackend.support.AuthTestHelper.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/** Integration test modul Schedule terhadap MySQL asli (butuh Docker). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@RecordApplicationEvents
class ScheduleFlowIntegrationTest {

    private static final String DAILY_BODY = """
            {"frequencyType":"DAILY","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-11",
             "times":[{"scheduledTime":"08:00:00"},{"scheduledTime":"20:00:00"}]}""";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private MedicationScheduleRepository scheduleRepository;

    // ---------------- autentikasi ----------------

    @Test
    void allEndpoints_requireAuthentication() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/schedules")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/medications/" + id + "/schedules").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/schedules/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/schedules/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/schedules/" + id)).andExpect(status().isUnauthorized());
    }

    // ---------------- create ----------------

    @Test
    void create_daily_returnsFullObject_andPublishesCreatedEvent() throws Exception {
        Session session = login();
        String medicationId = createMedication(session, "Paracetamol");

        createSchedule(session, medicationId, DAILY_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Schedule created successfully"))
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andExpect(jsonPath("$.data.medicationId").value(medicationId))
                .andExpect(jsonPath("$.data.frequencyType").value("DAILY"))
                .andExpect(jsonPath("$.data.intervalDays").isEmpty())
                .andExpect(jsonPath("$.data.doseQuantity").value(1.0))
                .andExpect(jsonPath("$.data.doseUnit").value("TABLET"))
                .andExpect(jsonPath("$.data.startDate").value("2026-11-11"))
                .andExpect(jsonPath("$.data.endDate").isEmpty())
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.times.length()").value(2))
                .andExpect(jsonPath("$.data.times[0].id").value(notNullValue()))
                .andExpect(jsonPath("$.data.times[0].scheduledTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.times[0].dayOfWeek").isEmpty())
                .andExpect(jsonPath("$.data.times[1].scheduledTime").value("20:00:00"));

        assertThat(events.stream(ScheduleChangedEvent.class).map(ScheduleChangedEvent::type).toList())
                .containsExactly(Type.CREATED);
    }

    @Test
    void create_weekly_andInterval_areAccepted_andWeeklyTimesAreSorted() throws Exception {
        Session session = login();
        String medicationId = createMedication(session, "Vitamin D");

        createSchedule(session, medicationId, """
                {"frequencyType":"WEEKLY","doseQuantity":0.5,"doseUnit":"TABLET","startDate":"2026-11-11",
                 "endDate":"2026-12-11",
                 "times":[{"scheduledTime":"08:00:00","dayOfWeek":4},{"scheduledTime":"08:00:00","dayOfWeek":1}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endDate").value("2026-12-11"))
                .andExpect(jsonPath("$.data.times[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.times[1].dayOfWeek").value(4));

        createSchedule(session, medicationId, """
                {"frequencyType":"INTERVAL","intervalDays":3,"doseQuantity":2,"doseUnit":"CAPSULE",
                 "startDate":"2026-11-11","times":[{"scheduledTime":"21:30:00"}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.intervalDays").value(3));
    }

    @Test
    void create_withInvalidConfiguration_returns422WithAllErrors() throws Exception {
        Session session = login();
        String medicationId = createMedication(session, "Paracetamol");

        createSchedule(session, medicationId, "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Invalid schedule configuration"))
                .andExpect(jsonPath("$.errors.frequencyType").value("Frequency type is required"))
                .andExpect(jsonPath("$.errors.doseQuantity").value("Dose quantity is required"))
                .andExpect(jsonPath("$.errors.doseUnit").value("Dose unit is required"))
                .andExpect(jsonPath("$.errors.startDate").value("Start date is required"))
                .andExpect(jsonPath("$.errors.times").value("At least one time is required"));

        createSchedule(session, medicationId, """
                {"frequencyType":"WEEKLY","doseQuantity":0,"doseUnit":"TABLET","startDate":"2026-11-11",
                 "endDate":"2026-11-01","times":[{"scheduledTime":"08:00:00","dayOfWeek":9},{"scheduledTime":"09:00:00"}]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.doseQuantity").value("Dose quantity must be greater than 0"))
                .andExpect(jsonPath("$.errors.endDate").value("End date must not be earlier than start date"))
                .andExpect(jsonPath("$.errors['times[0].dayOfWeek']").value("Day of week must be between 1 and 7"))
                .andExpect(jsonPath("$.errors['times[1].dayOfWeek']")
                        .value("Day of week is required for WEEKLY schedules"));

        createSchedule(session, medicationId, """
                {"frequencyType":"INTERVAL","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-11",
                 "times":[{"scheduledTime":"08:00:00"},{"scheduledTime":"08:00:00"}]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.intervalDays")
                        .value("Interval days must be at least 1 for INTERVAL schedules"))
                .andExpect(jsonPath("$.errors.times").value("Duplicate times are not allowed"));
    }

    @Test
    void create_withUnreadableValues_returns422PerField() throws Exception {
        Session session = login();
        String medicationId = createMedication(session, "Paracetamol");

        createSchedule(session, medicationId, """
                {"frequencyType":"MONTHLY","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-11",
                 "times":[{"scheduledTime":"08:00:00"}]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.frequencyType").value("Value must be one of: DAILY, WEEKLY, INTERVAL"));

        createSchedule(session, medicationId, """
                {"frequencyType":"DAILY","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-11",
                 "times":[{"scheduledTime":"25:00:00"}]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors['times[0].scheduledTime']").value("Invalid value"));

        createSchedule(session, medicationId, "{not json").andExpect(status().isBadRequest());
    }

    @Test
    void create_forUnknownOtherUsersOrInactiveMedication() throws Exception {
        Session alice = login();
        Session bob = login();
        String aliceMedication = createMedication(alice, "Obat Alice");

        createSchedule(alice, UUID.randomUUID().toString(), DAILY_BODY).andExpect(status().isNotFound());
        createSchedule(bob, aliceMedication, DAILY_BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Medication not found"));

        mvc.perform(patch("/api/medications/" + aliceMedication).header("Authorization", alice.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}")).andExpect(status().isOk());
        createSchedule(alice, aliceMedication, DAILY_BODY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.medicationId").value("Medication is not active"));
    }

    // ---------------- get & list ----------------

    @Test
    void get_andOwnership() throws Exception {
        Session alice = login();
        Session bob = login();
        String scheduleId = createScheduleId(alice, createMedication(alice, "Obat Alice"), DAILY_BODY);

        mvc.perform(get("/api/schedules/" + scheduleId).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(scheduleId));

        mvc.perform(get("/api/schedules/" + scheduleId).header("Authorization", bob.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Schedule not found"));
        patchSchedule(bob, scheduleId, "{\"doseQuantity\":9}").andExpect(status().isNotFound());
        mvc.perform(delete("/api/schedules/" + scheduleId).header("Authorization", bob.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/schedules").header("Authorization", bob.bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(get("/api/schedules/" + UUID.randomUUID()).header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/schedules/bukan-uuid").header("Authorization", alice.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_filtersByMedicationAndActive_newestFirst() throws Exception {
        Session session = login();
        String med1 = createMedication(session, "Obat 1");
        String med2 = createMedication(session, "Obat 2");
        String first = createScheduleId(session, med1, DAILY_BODY);
        Thread.sleep(15);
        String second = createScheduleId(session, med2, DAILY_BODY);
        Thread.sleep(15);
        String third = createScheduleId(session, med1, DAILY_BODY);

        mvc.perform(get("/api/schedules").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(third))
                .andExpect(jsonPath("$.data[1].id").value(second))
                .andExpect(jsonPath("$.data[2].id").value(first))
                .andExpect(jsonPath("$.data[0].times.length()").value(2));

        mvc.perform(get("/api/schedules?medicationId=" + med1).header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(third))
                .andExpect(jsonPath("$.data[1].id").value(first));

        patchSchedule(session, third, "{\"isActive\":false}").andExpect(status().isOk());

        mvc.perform(get("/api/schedules?active=false").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(third));
        mvc.perform(get("/api/schedules?medicationId=" + med1 + "&active=true")
                        .header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(first));
    }

    // ---------------- update ----------------

    @Test
    void update_changesFields_andPublishesEventOnlyOnRealChange() throws Exception {
        Session session = login();
        String scheduleId = createScheduleId(session, createMedication(session, "Paracetamol"), DAILY_BODY);

        patchSchedule(session, scheduleId, "{\"doseQuantity\":2,\"doseUnit\":\"CAPSULE\",\"endDate\":\"2026-12-31\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Schedule updated successfully"))
                .andExpect(jsonPath("$.data.doseQuantity").value(2.0))
                .andExpect(jsonPath("$.data.doseUnit").value("CAPSULE"))
                .andExpect(jsonPath("$.data.endDate").value("2026-12-31"));

        patchSchedule(session, scheduleId, "{}").andExpect(status().isOk());                   // tanpa perubahan
        patchSchedule(session, scheduleId, "{\"doseQuantity\":2.00}").andExpect(status().isOk()); // nilai sama

        // endDate: null = dikosongkan
        patchSchedule(session, scheduleId, "{\"endDate\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endDate").isEmpty());

        // CREATED sekali, UPDATED hanya untuk dua perubahan nyata di atas.
        assertThat(events.stream(ScheduleChangedEvent.class).map(ScheduleChangedEvent::type).toList())
                .containsExactly(Type.CREATED, Type.UPDATED, Type.UPDATED);
    }

    @Test
    void update_times_keepsIdsOfUnchangedRows_removesOldAndAddsNew() throws Exception {
        Session session = login();
        String scheduleId = createScheduleId(session, createMedication(session, "Paracetamol"), DAILY_BODY);
        String json = getSchedule(session, scheduleId);
        String id0800 = JsonPath.read(json, "$.data.times[0].id");
        String id2000 = JsonPath.read(json, "$.data.times[1].id");

        patchSchedule(session, scheduleId,
                "{\"times\":[{\"scheduledTime\":\"08:00:00\"},{\"scheduledTime\":\"12:00:00\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.times.length()").value(2))
                .andExpect(jsonPath("$.data.times[0].scheduledTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.times[0].id").value(id0800))        // dipertahankan
                .andExpect(jsonPath("$.data.times[1].scheduledTime").value("12:00:00"));

        String after = getSchedule(session, scheduleId);
        String newId = JsonPath.read(after, "$.data.times[1].id");
        assertThat(newId).isNotEqualTo(id2000);                                   // 20:00 hilang, 12:00 baru
        assertThat(after).doesNotContain(id2000);
    }

    @Test
    void update_frequency_switchRules() throws Exception {
        Session session = login();
        String scheduleId = createScheduleId(session, createMedication(session, "Paracetamol"), """
                {"frequencyType":"INTERVAL","intervalDays":3,"doseQuantity":1,"doseUnit":"TABLET",
                 "startDate":"2026-11-11","times":[{"scheduledTime":"08:00:00"}]}""");

        // INTERVAL -> DAILY: intervalDays otomatis dikosongkan, jam lama tetap berlaku.
        patchSchedule(session, scheduleId, "{\"frequencyType\":\"DAILY\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frequencyType").value("DAILY"))
                .andExpect(jsonPath("$.data.intervalDays").isEmpty());

        // DAILY -> WEEKLY tanpa jam baru: ditolak.
        patchSchedule(session, scheduleId, "{\"frequencyType\":\"WEEKLY\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Invalid schedule configuration"))
                .andExpect(jsonPath("$.errors.times")
                        .value("Times must be provided when changing frequency type to or from WEEKLY"));

        // DAILY -> WEEKLY dengan jam baru: diterima.
        patchSchedule(session, scheduleId,
                "{\"frequencyType\":\"WEEKLY\",\"times\":[{\"scheduledTime\":\"08:00:00\",\"dayOfWeek\":1}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frequencyType").value("WEEKLY"))
                .andExpect(jsonPath("$.data.times[0].dayOfWeek").value(1));
    }

    @Test
    void update_withInvalidValues_returns422_andChangesNothing() throws Exception {
        Session session = login();
        String scheduleId = createScheduleId(session, createMedication(session, "Paracetamol"), DAILY_BODY);

        patchSchedule(session, scheduleId, "{\"endDate\":\"2026-01-01\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.endDate").value("End date must not be earlier than start date"));

        patchSchedule(session, scheduleId, "{\"startDate\":\"2026-11-20\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.startDate").value("Start date cannot be changed"));

        patchSchedule(session, scheduleId, "{\"times\":[]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.times").value("At least one time is required"));

        patchSchedule(session, scheduleId, "{\"startDate\":\"2026-11-11\"}").andExpect(status().isOk()); // sama: boleh

        mvc.perform(get("/api/schedules/" + scheduleId).header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.endDate").isEmpty())
                .andExpect(jsonPath("$.data.times.length()").value(2));
    }

    // ---------------- delete ----------------

    @Test
    void delete_isSoftDelete_hidesSchedule_andPublishesEvent() throws Exception {
        Session session = login();
        String scheduleId = createScheduleId(session, createMedication(session, "Paracetamol"), DAILY_BODY);

        mvc.perform(delete("/api/schedules/" + scheduleId).header("Authorization", session.bearer()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mvc.perform(get("/api/schedules/" + scheduleId).header("Authorization", session.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/schedules").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));
        patchSchedule(session, scheduleId, "{\"doseQuantity\":3}").andExpect(status().isNotFound());
        mvc.perform(delete("/api/schedules/" + scheduleId).header("Authorization", session.bearer()))
                .andExpect(status().isNotFound());

        assertThat(scheduleRepository.findById(UUID.fromString(scheduleId))).hasValueSatisfying(schedule -> {
            assertThat(schedule.getDeletedAt()).isNotNull();
            assertThat(schedule.isActive()).isFalse();
        });
        assertThat(events.stream(ScheduleChangedEvent.class).map(ScheduleChangedEvent::type).toList())
                .containsExactly(Type.CREATED, Type.DELETED);
    }

    @Test
    void deletingMedication_softDeletesItsSchedules() throws Exception {
        Session session = login();
        String medicationId = createMedication(session, "Paracetamol");
        String scheduleId = createScheduleId(session, medicationId, DAILY_BODY);

        mvc.perform(delete("/api/medications/" + medicationId).header("Authorization", session.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/schedules/" + scheduleId).header("Authorization", session.bearer()))
                .andExpect(status().isNotFound());
        assertThat(scheduleRepository.findById(UUID.fromString(scheduleId))).hasValueSatisfying(schedule -> {
            assertThat(schedule.getDeletedAt()).isNotNull();      // dibuktikan: listener yang menghapusnya
            assertThat(schedule.isActive()).isFalse();
        });
    }

    // ---------------- helper ----------------

    private Session login() throws Exception {
        return AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");
    }

    private String createMedication(Session session, String name) throws Exception {
        String json = mvc.perform(post("/api/medications").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.id");
    }

    private ResultActions createSchedule(Session session, String medicationId, String body) throws Exception {
        return mvc.perform(post("/api/medications/" + medicationId + "/schedules")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createScheduleId(Session session, String medicationId, String body) throws Exception {
        String json = createSchedule(session, medicationId, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.id");
    }

    private ResultActions patchSchedule(Session session, String scheduleId, String body) throws Exception {
        return mvc.perform(patch("/api/schedules/" + scheduleId).header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String getSchedule(Session session, String scheduleId) throws Exception {
        return mvc.perform(get("/api/schedules/" + scheduleId).header("Authorization", session.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
