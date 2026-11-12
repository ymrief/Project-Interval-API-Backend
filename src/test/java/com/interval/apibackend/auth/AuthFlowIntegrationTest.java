package com.interval.apibackend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/**
 * Integration test end-to-end terhadap MySQL asli (butuh Docker).
 * Flyway menjalankan V1__init_schema.sql dan Hibernate memvalidasi pemetaan entity,
 * sehingga test ini sekaligus memverifikasi CHAR(36) / DATETIME(3) / CHAR(64).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "StrongPassword123";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;

    // ---------------- register ----------------

    @Test
    void register_normalizesEmail_andRejectsDuplicateIgnoringCase() throws Exception {
        String local = "user-" + UUID.randomUUID();

        register("John Doe", local + "@Example.com", PASSWORD, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(local + "@example.com"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Jakarta"));

        register("John Doe", local + "@EXAMPLE.COM", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").value("Email already exists"));
    }

    @Test
    void register_withInvalidInput_returns422() throws Exception {
        register("John Doe", newEmail(), "short", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.password").value("Password must be between 8 and 72 characters"));

        register("John Doe", newEmail(), PASSWORD, "Mars/Olympus")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.timezone").value("Timezone is not a valid IANA zone id"));

        register("John Doe", "bukan-email", PASSWORD, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.email").value("Email format is invalid"));
    }

    @Test
    void register_withPasswordOver72Bytes_returns422() throws Exception {
        // 40 karakter x 2 byte = 80 byte: lolos @Size (40 karakter) tetapi melebihi batas BCrypt.
        register("John Doe", newEmail(), "é".repeat(40), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.password").value("Password must be between 8 and 72 characters"));
    }

    // ---------------- login ----------------

    @Test
    void login_withWrongPasswordOrUnknownEmail_returns401WithSameMessage() throws Exception {
        String email = newEmail();
        register("John Doe", email, PASSWORD, null).andExpect(status().isCreated());

        login(email, "WrongPassword999")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));

        login(newEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }

    @Test
    void login_succeeds_ignoringEmailCase() throws Exception {
        String email = newEmail();
        register("John Doe", email, PASSWORD, "Asia/Makassar").andExpect(status().isCreated());

        login(email.toUpperCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.timezone").value("Asia/Makassar"));
    }

    // ---------------- proteksi endpoint ----------------

    @Test
    void protectedEndpoint_withoutOrWithInvalidToken_returns401InSpecFormat() throws Exception {
        mvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    // ---------------- refresh ----------------

    @Test
    void refresh_rotatesToken_andReuseOfOldTokenRevokesAllSessions() throws Exception {
        Session session = registerAndLogin();

        String second = refreshToken(session.refreshToken());           // rotasi: token #1 -> #2
        refresh(session.refreshToken()).andExpect(status().isUnauthorized()); // pakai ulang #1 => bocor
        refresh(second).andExpect(status().isUnauthorized());           // #2 ikut dicabut
    }

    @Test
    void refresh_withUnknownToken_returns401() throws Exception {
        refresh("token-yang-tidak-ada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token is invalid or expired"));
    }

    // ---------------- logout ----------------

    @Test
    void logout_revokesRefreshToken_andIsIdempotent() throws Exception {
        Session session = registerAndLogin();

        logout(session).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Logout successful"));
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
        logout(session).andExpect(status().isOk());
    }

    @Test
    void logout_doesNotRevokeOtherUsersToken() throws Exception {
        Session alice = registerAndLogin();
        Session bob = registerAndLogin();

        // Bob mencoba mencabut token milik Alice: tetap 200 (idempotent), tetapi token Alice masih hidup.
        mvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + bob.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + alice.refreshToken() + "\"}"))
                .andExpect(status().isOk());

        refresh(alice.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void logoutAll_revokesEverySessionOfTheUser() throws Exception {
        String email = newEmail();
        register("John Doe", email, PASSWORD, null).andExpect(status().isCreated());
        Session first = loginSession(email);
        Session second = loginSession(email);

        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All sessions have been logged out"));

        refresh(first.refreshToken()).andExpect(status().isUnauthorized());
        refresh(second.refreshToken()).andExpect(status().isUnauthorized());
    }

    // ---------------- helper ----------------

    private record Session(String accessToken, String refreshToken) {
    }

    private static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions register(String fullName, String email, String password, String timezone) throws Exception {
        String timezoneJson = timezone == null ? "" : ",\"timezone\":\"" + timezone + "\"";
        String body = "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"" + timezoneJson + "}";
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private String refreshToken(String refreshToken) throws Exception {
        String json = refresh(refreshToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.refreshToken");
    }

    private ResultActions logout(Session session) throws Exception {
        return mvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + session.refreshToken() + "\"}"));
    }

    private Session loginSession(String email) throws Exception {
        String json = login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Session(JsonPath.read(json, "$.data.accessToken"), JsonPath.read(json, "$.data.refreshToken"));
    }

    private Session registerAndLogin() throws Exception {
        String email = newEmail();
        register("John Doe", email, PASSWORD, null).andExpect(status().isCreated());
        return loginSession(email);
    }
}
