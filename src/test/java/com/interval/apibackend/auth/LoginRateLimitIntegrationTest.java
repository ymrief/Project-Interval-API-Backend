package com.interval.apibackend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import com.interval.apibackend.auth.service.LoginRateLimiter;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.interval.apibackend.support.AuthTestHelper;
import com.interval.apibackend.support.AuthTestHelper.Session;
import com.interval.apibackend.support.MutableClock;
import com.interval.apibackend.support.TestClockConfig;

/** Rate limiting login end-to-end (butuh Docker). Batas bawaan: 5 kegagalan per email, jendela 15 menit. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestClockConfig.class)
@TestPropertySource(properties = "interval.scheduling.enabled=false")
class LoginRateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private LoginRateLimiter limiter;

    @BeforeEach
    void reset() {
        clock.set(TestClockConfig.BASE);
        limiter.clear();       // semua request test berasal dari IP yang sama (127.0.0.1)
    }

    @Test
    void afterFiveWrongPasswords_loginIsBlocked_evenWithTheCorrectPassword() throws Exception {
        Session user = AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");

        for (int i = 0; i < 5; i++) {
            login(user.email(), "SalahSalah123").andExpect(status().isUnauthorized());
        }

        login(user.email(), "SalahSalah123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Too many login attempts. Please try again later"))
                .andExpect(jsonPath("$.errors").isEmpty());

        // Password yang benar pun ditolak selama diblokir: penyerang tidak mendapat kesempatan menebak.
        login(user.email(), AuthTestHelper.PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void afterTheWindow_loginWorksAgain() throws Exception {
        Session user = AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");
        for (int i = 0; i < 5; i++) {
            login(user.email(), "SalahSalah123").andExpect(status().isUnauthorized());
        }
        login(user.email(), AuthTestHelper.PASSWORD).andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofMinutes(15));

        login(user.email(), AuthTestHelper.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmail_isThrottledExactlyLikeAnExistingOne_soNothingLeaks() throws Exception {
        String ghost = "ghost-" + UUID.randomUUID() + "@example.com";

        for (int i = 0; i < 5; i++) {
            login(ghost, "SalahSalah123").andExpect(status().isUnauthorized());
        }

        login(ghost, "SalahSalah123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.message").value("Too many login attempts. Please try again later"));
    }

    @Test
    void aSuccessfulLogin_resetsTheFailureCounter() throws Exception {
        Session user = AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");

        for (int i = 0; i < 4; i++) {
            login(user.email(), "SalahSalah123").andExpect(status().isUnauthorized());
        }
        login(user.email(), AuthTestHelper.PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(user.email(), "SalahSalah123").andExpect(status().isUnauthorized());   // bukan 429
        }
    }

    @Test
    void aBlockedAccount_doesNotAffectOtherAccounts() throws Exception {
        Session victim = AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");
        for (int i = 0; i < 5; i++) {
            login(victim.email(), "SalahSalah123").andExpect(status().isUnauthorized());
        }
        login(victim.email(), AuthTestHelper.PASSWORD).andExpect(status().isTooManyRequests());

        Session other = AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");     // IP sama, email lain
        login(other.email(), AuthTestHelper.PASSWORD).andExpect(status().isOk());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
