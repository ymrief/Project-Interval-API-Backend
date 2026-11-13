package com.interval.apibackend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** Membuat pengguna baru yang sudah login, untuk dipakai integration test modul lain. */
public final class AuthTestHelper {

    public static final String PASSWORD = "StrongPassword123";

    public record Session(String email, String accessToken, String refreshToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private AuthTestHelper() {
    }

    public static Session registerAndLogin(MockMvc mvc, String timezone) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"John Doe\",\"email\":\"" + email + "\",\"password\":\""
                                + PASSWORD + "\",\"timezone\":\"" + timezone + "\"}"))
                .andExpect(status().isCreated());

        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return new Session(email, JsonPath.read(json, "$.data.accessToken"),
                JsonPath.read(json, "$.data.refreshToken"));
    }
}
