package com.interval.apibackend.user;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.interval.apibackend.user.event.UserTimezoneChangedEvent;
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

import static org.assertj.core.api.Assertions.assertThat;

/** Integration test modul User terhadap MySQL asli (butuh Docker). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@RecordApplicationEvents
class UserFlowIntegrationTest {

    private static final String PASSWORD = "StrongPassword123";
    private static final String NEW_PASSWORD = "NewStrongPassword456";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationEvents events;

    // ---------------- autentikasi ----------------

    @Test
    void allEndpoints_requireAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- GET /me ----------------

    @Test
    void getMe_returnsProfile_withCreatedAtInUserTimezone_andNoSecrets() throws Exception {
        Session session = registerAndLogin("Asia/Makassar");

        mvc.perform(get("/api/users/me").header("Authorization", bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User profile retrieved successfully"))
                .andExpect(jsonPath("$.data.email").value(session.email()))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Makassar"))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.createdAt").value(endsWith("+08:00")))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    // ---------------- PATCH /me ----------------

    @Test
    void updateProfile_trimsName_andDoesNotPublishEventWithoutTimezoneChange() throws Exception {
        Session session = registerAndLogin("Asia/Jakarta");

        patchMe(session, "{\"fullName\":\"  Jane Smith  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"))
                .andExpect(jsonPath("$.data.fullName").value("Jane Smith"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Jakarta"));

        assertThat(events.stream(UserTimezoneChangedEvent.class).count()).isZero();
    }

    @Test
    void updateProfile_timezoneChange_publishesEventOnce_andChangesResponseOffset() throws Exception {
        Session session = registerAndLogin("Asia/Jakarta");

        patchMe(session, "{\"timezone\":\"Asia/Makassar\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("Asia/Makassar"));

        assertThat(events.stream(UserTimezoneChangedEvent.class).toList())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.oldTimezone()).isEqualTo("Asia/Jakarta");
                    assertThat(event.newTimezone()).isEqualTo("Asia/Makassar");
                });

        mvc.perform(get("/api/users/me").header("Authorization", bearer(session)))
                .andExpect(jsonPath("$.data.createdAt").value(endsWith("+08:00")));

        // Mengirim timezone yang sama lagi bukan perubahan: tidak ada event baru.
        patchMe(session, "{\"timezone\":\"Asia/Makassar\"}").andExpect(status().isOk());
        assertThat(events.stream(UserTimezoneChangedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void updateProfile_withInvalidValues_returns422_andLeavesProfileUnchanged() throws Exception {
        Session session = registerAndLogin("Asia/Jakarta");

        patchMe(session, "{\"fullName\":\"   \"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.fullName").value("Full name must not be blank"));

        patchMe(session, "{\"fullName\":\"A\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.fullName").value("Full name must be between 2 and 100 characters"));

        patchMe(session, "{\"timezone\":\"Mars/Olympus\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.timezone").value("Timezone is not a valid IANA zone id"));

        mvc.perform(get("/api/users/me").header("Authorization", bearer(session)))
                .andExpect(jsonPath("$.data.fullName").value("John Doe"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Jakarta"));
    }

    @Test
    void updateProfile_emptyBody_isNoOp_andEmailCannotBeChanged() throws Exception {
        Session session = registerAndLogin("Asia/Jakarta");

        patchMe(session, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("John Doe"));

        patchMe(session, "{\"email\":\"hacker@example.com\",\"isActive\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(session.email()));
    }

    // ---------------- PATCH /me/password ----------------

    @Test
    void changePassword_withWrongCurrentPassword_returns422() throws Exception {
        Session session = registerAndLogin("Asia/Jakarta");

        changePassword(session, "SalahSalah123", NEW_PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"))
                .andExpect(jsonPath("$.errors.currentPassword").value("Password does not match"));
    }

    @Test
    void changePassword_withInvalidNewPassword_returns422() throws Exception {
        Session session = registerAndLogin("Asia/Jakarta");

        changePassword(session, PASSWORD, PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.newPassword")
                        .value("New password must be different from the current password"));

        changePassword(session, PASSWORD, "short")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.newPassword").value("New password must be between 8 and 72 characters"));

        // 40 x 2 byte = 80 byte: lolos @Size (karakter) tetapi melebihi batas BCrypt.
        changePassword(session, PASSWORD, "é".repeat(40))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.newPassword").value("New password must be between 8 and 72 characters"));
    }

    @Test
    void changePassword_success_revokesAllOldSessions_andReturnsWorkingTokens() throws Exception {
        Session current = registerAndLogin("Asia/Jakarta");
        Session otherDevice = login(current.email(), PASSWORD);

        String json = changePassword(current, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = JsonPath.read(json, "$.data.accessToken");
        String newRefreshToken = JsonPath.read(json, "$.data.refreshToken");

        // Semua refresh token lama (perangkat ini dan perangkat lain) sudah dicabut.
        refresh(current.refreshToken()).andExpect(status().isUnauthorized());
        refresh(otherDevice.refreshToken()).andExpect(status().isUnauthorized());

        // Pasangan token baru berfungsi.
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());
        refresh(newRefreshToken).andExpect(status().isOk());

        // Password lama tidak lagi berlaku, password baru berlaku.
        loginRequest(current.email(), PASSWORD).andExpect(status().isUnauthorized());
        loginRequest(current.email(), NEW_PASSWORD).andExpect(status().isOk());
    }

    // ---------------- helper ----------------

    private record Session(String email, String accessToken, String refreshToken) {
    }

    private static String bearer(Session session) {
        return "Bearer " + session.accessToken();
    }

    private Session registerAndLogin(String timezone) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"John Doe\",\"email\":\"" + email + "\",\"password\":\""
                                + PASSWORD + "\",\"timezone\":\"" + timezone + "\"}"))
                .andExpect(status().isCreated());
        return login(email, PASSWORD);
    }

    private Session login(String email, String password) throws Exception {
        String json = loginRequest(email, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Session(email, JsonPath.read(json, "$.data.accessToken"),
                JsonPath.read(json, "$.data.refreshToken"));
    }

    private ResultActions loginRequest(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private ResultActions patchMe(Session session, String body) throws Exception {
        return mvc.perform(patch("/api/users/me").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changePassword(Session session, String current, String next) throws Exception {
        return mvc.perform(patch("/api/users/me/password").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }
}
