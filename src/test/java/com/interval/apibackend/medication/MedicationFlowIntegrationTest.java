    package com.interval.apibackend.medication;

    import static org.assertj.core.api.Assertions.assertThat;
    import static org.hamcrest.Matchers.endsWith;
    import static org.hamcrest.Matchers.notNullValue;
    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
    import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
    import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
    import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

    import java.util.List;
    import java.util.UUID;

    import com.interval.apibackend.medication.event.MedicationLifecycleEvent;
    import com.interval.apibackend.medication.event.MedicationLifecycleEvent.Type;
    import com.interval.apibackend.medication.repository.MedicationRepository;
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

    /** Integration test modul Medication terhadap MySQL asli (butuh Docker). */
    @SpringBootTest
    @AutoConfigureMockMvc
    @Testcontainers
    @RecordApplicationEvents
    class MedicationFlowIntegrationTest {

        @Container
        @ServiceConnection
        static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

        @Autowired
        private MockMvc mvc;

        @Autowired
        private ApplicationEvents events;

        @Autowired
        private MedicationRepository medicationRepository;

        // ---------------- autentikasi ----------------

        @Test
        void allEndpoints_requireAuthentication() throws Exception {
            String id = UUID.randomUUID().toString();
            mvc.perform(get("/api/medications")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/medications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/medications/" + id)).andExpect(status().isUnauthorized());
            mvc.perform(patch("/api/medications/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete("/api/medications/" + id)).andExpect(status().isUnauthorized());
        }

        // ---------------- create ----------------

        @Test
        void create_returnsFullObject_trimsInput_andNormalizesBlankToNull() throws Exception {
            Session session = login("Asia/Jakarta");

            create(session, "{\"name\":\"  Paracetamol  \",\"dosage\":\"500 mg\",\"dosageForm\":\"TABLET\","
                    + "\"instructions\":\"Diminum setelah makan\",\"notes\":\"   \",\"imageUrl\":null}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Medication created successfully"))
                    .andExpect(jsonPath("$.data.id").value(notNullValue()))
                    .andExpect(jsonPath("$.data.name").value("Paracetamol"))
                    .andExpect(jsonPath("$.data.dosage").value("500 mg"))
                    .andExpect(jsonPath("$.data.dosageForm").value("TABLET"))
                    .andExpect(jsonPath("$.data.instructions").value("Diminum setelah makan"))
                    .andExpect(jsonPath("$.data.notes").isEmpty())
                    .andExpect(jsonPath("$.data.imageUrl").isEmpty())
                    .andExpect(jsonPath("$.data.isActive").value(true))
                    .andExpect(jsonPath("$.data.createdAt").value(endsWith("+07:00")))
                    .andExpect(jsonPath("$.data.updatedAt").value(endsWith("+07:00")))
                    .andExpect(jsonPath("$.data.userId").doesNotExist());
        }

        @Test
        void create_withOnlyName_defaultsDosageFormToOther() throws Exception {
            Session session = login("Asia/Jakarta");

            create(session, "{\"name\":\"Vitamin C\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.dosageForm").value("OTHER"));
        }

        @Test
        void create_withInvalidInput_returns422() throws Exception {
            Session session = login("Asia/Jakarta");

            create(session, "{\"dosage\":\"500 mg\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.name").value("Medication name is required"));

            create(session, "{\"name\":\"" + "x".repeat(151) + "\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors.name").value("Medication name must be at most 150 characters"));

            create(session, "{\"name\":\"Obat\",\"dosageForm\":\"PILL\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors.dosageForm")
                            .value("Value must be one of: TABLET, CAPSULE, SYRUP, DROPS, OTHER"));

            create(session, "{\"name\":\"Obat\",\"imageUrl\":\"javascript:alert(1)\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors.imageUrl").value("Image URL must start with http:// or https://"));
        }

        @Test
        void create_withMalformedJson_returns400() throws Exception {
            Session session = login("Asia/Jakarta");

            create(session, "{not json").andExpect(status().isBadRequest());
        }

        // ---------------- list ----------------

        @Test
        void list_isPaginated_newestFirst_andFilterableByActive() throws Exception {
            Session session = login("Asia/Jakarta");
            String first = createId(session, "Obat A");
            Thread.sleep(15);
            String second = createId(session, "Obat B");
            Thread.sleep(15);
            String third = createId(session, "Obat C");

            mvc.perform(get("/api/medications?page=0&size=2").header("Authorization", session.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalItems").value(3))
                    .andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(2))
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andExpect(jsonPath("$.data.items[0].id").value(third))
                    .andExpect(jsonPath("$.data.items[1].id").value(second));

            mvc.perform(get("/api/medications?page=1&size=2").header("Authorization", session.bearer()))
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(first));

            patchMedication(session, third, "{\"isActive\":false}").andExpect(status().isOk());

            mvc.perform(get("/api/medications?active=false").header("Authorization", session.bearer()))
                    .andExpect(jsonPath("$.data.totalItems").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(third));
            mvc.perform(get("/api/medications?active=true").header("Authorization", session.bearer()))
                    .andExpect(jsonPath("$.data.totalItems").value(2));
        }

        @Test
        void list_withInvalidPaging_returns422_orMalformedType_returns400() throws Exception {
            Session session = login("Asia/Jakarta");

            mvc.perform(get("/api/medications?size=101").header("Authorization", session.bearer()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors.size").value("Size must be between 1 and 100"));

            mvc.perform(get("/api/medications?size=0").header("Authorization", session.bearer()))
                    .andExpect(status().isUnprocessableEntity());

            mvc.perform(get("/api/medications?page=-1").header("Authorization", session.bearer()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors.page").value("Page must not be negative"));

            mvc.perform(get("/api/medications?page=abc").header("Authorization", session.bearer()))
                    .andExpect(status().isBadRequest());
        }

        // ---------------- kepemilikan data ----------------

        @Test
        void otherUsersMedication_isInvisible_andUntouchable() throws Exception {
            Session alice = login("Asia/Jakarta");
            Session bob = login("Asia/Jakarta");
            String aliceMedication = createId(alice, "Obat Alice");

            mvc.perform(get("/api/medications/" + aliceMedication).header("Authorization", bob.bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Medication not found"));
            patchMedication(bob, aliceMedication, "{\"name\":\"Diretas\"}").andExpect(status().isNotFound());
            mvc.perform(delete("/api/medications/" + aliceMedication).header("Authorization", bob.bearer()))
                    .andExpect(status().isNotFound());

            mvc.perform(get("/api/medications").header("Authorization", bob.bearer()))
                    .andExpect(jsonPath("$.data.totalItems").value(0));

            // Milik Alice tetap utuh.
            mvc.perform(get("/api/medications/" + aliceMedication).header("Authorization", alice.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Obat Alice"));
        }

        // ---------------- detail ----------------

        @Test
        void get_withUnknownOrMalformedId() throws Exception {
            Session session = login("Asia/Jakarta");

            mvc.perform(get("/api/medications/" + UUID.randomUUID()).header("Authorization", session.bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Medication not found"));

            mvc.perform(get("/api/medications/bukan-uuid").header("Authorization", session.bearer()))
                    .andExpect(status().isBadRequest());
        }

        // ---------------- update ----------------

        @Test
        void update_changesOnlySentFields_andDistinguishesAbsentFromNull() throws Exception {
            Session session = login("Asia/Jakarta");
            String id = createIdFrom(session, "{\"name\":\"Paracetamol\",\"dosage\":\"500 mg\","
                    + "\"instructions\":\"Setelah makan\",\"notes\":\"Catatan\"}");

            // hanya name
            patchMedication(session, id, "{\"name\":\"Paracetamol 500 mg\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Medication updated successfully"))
                    .andExpect(jsonPath("$.data.name").value("Paracetamol 500 mg"))
                    .andExpect(jsonPath("$.data.dosage").value("500 mg"))
                    .andExpect(jsonPath("$.data.instructions").value("Setelah makan"))
                    .andExpect(jsonPath("$.data.notes").value("Catatan"));

            // notes: null -> dikosongkan, field lain tidak tersentuh
            patchMedication(session, id, "{\"notes\":null}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.notes").isEmpty())
                    .andExpect(jsonPath("$.data.instructions").value("Setelah makan"));

            // name: null = tidak diubah; dosage berisi spasi = dikosongkan
            patchMedication(session, id, "{\"name\":null,\"dosage\":\"   \"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Paracetamol 500 mg"))
                    .andExpect(jsonPath("$.data.dosage").isEmpty());

            // body kosong = tidak ada perubahan
            patchMedication(session, id, "{}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Paracetamol 500 mg"));
        }

        @Test
        void update_withInvalidValues_returns422_andChangesNothing() throws Exception {
            Session session = login("Asia/Jakarta");
            String id = createId(session, "Paracetamol");

            patchMedication(session, id, "{\"name\":\"   \"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors.name").value("Medication name must not be blank"));

            patchMedication(session, id, "{\"dosageForm\":\"PILL\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors.dosageForm")
                            .value("Value must be one of: TABLET, CAPSULE, SYRUP, DROPS, OTHER"));

            mvc.perform(get("/api/medications/" + id).header("Authorization", session.bearer()))
                    .andExpect(jsonPath("$.data.name").value("Paracetamol"));
        }

        @Test
        void update_isActive_publishesLifecycleEventOnlyOnRealChange() throws Exception {
            Session session = login("Asia/Jakarta");
            String id = createId(session, "Paracetamol");

            patchMedication(session, id, "{\"isActive\":false}").andExpect(jsonPath("$.data.isActive").value(false));
            patchMedication(session, id, "{\"isActive\":false}").andExpect(status().isOk());   // sama: tanpa event
            patchMedication(session, id, "{\"isActive\":true}").andExpect(jsonPath("$.data.isActive").value(true));
            patchMedication(session, id, "{\"isActive\":true}").andExpect(status().isOk());    // sama: tanpa event

            List<Type> types = events.stream(MedicationLifecycleEvent.class)
                    .map(MedicationLifecycleEvent::type).toList();
            assertThat(types).containsExactly(Type.PAUSED, Type.RESUMED);
        }

        // ---------------- delete ----------------

        @Test
        void delete_isSoftDelete_hidesMedication_andPublishesEvent() throws Exception {
            Session session = login("Asia/Jakarta");
            String id = createId(session, "Paracetamol");

            mvc.perform(delete("/api/medications/" + id).header("Authorization", session.bearer()))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            // Hilang dari detail, list, dan tidak bisa diubah/dihapus lagi.
            mvc.perform(get("/api/medications/" + id).header("Authorization", session.bearer()))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/medications").header("Authorization", session.bearer()))
                    .andExpect(jsonPath("$.data.totalItems").value(0));
            patchMedication(session, id, "{\"name\":\"Hidup lagi\"}").andExpect(status().isNotFound());
            mvc.perform(delete("/api/medications/" + id).header("Authorization", session.bearer()))
                    .andExpect(status().isNotFound());

            // Barisnya tetap ada di database (soft delete), tidak aktif, dan deletedAt terisi.
            assertThat(medicationRepository.findById(UUID.fromString(id))).hasValueSatisfying(medication -> {
                assertThat(medication.getDeletedAt()).isNotNull();
                assertThat(medication.isActive()).isFalse();
            });

            assertThat(events.stream(MedicationLifecycleEvent.class).map(MedicationLifecycleEvent::type).toList())
                    .containsExactly(Type.DELETED);
        }

        // ---------------- helper ----------------

        private Session login(String timezone) throws Exception {
            return AuthTestHelper.registerAndLogin(mvc, timezone);
        }

        private ResultActions create(Session session, String body) throws Exception {
            return mvc.perform(post("/api/medications").header("Authorization", session.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }

        private String createId(Session session, String name) throws Exception {
            return createIdFrom(session, "{\"name\":\"" + name + "\"}");
        }

        private String createIdFrom(Session session, String body) throws Exception {
            String json = create(session, body).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.read(json, "$.data.id");
        }

        private ResultActions patchMedication(Session session, String id, String body) throws Exception {
            return mvc.perform(patch("/api/medications/" + id).header("Authorization", session.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }
    }
