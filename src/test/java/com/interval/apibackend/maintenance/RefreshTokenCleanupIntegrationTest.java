package com.interval.apibackend.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.interval.apibackend.auth.repository.RefreshTokenRepository;
import com.interval.apibackend.auth.service.TokenUtils;
import com.interval.apibackend.maintenance.service.RefreshTokenCleanupService;
import com.interval.apibackend.support.AuthTestHelper;
import com.interval.apibackend.support.AuthTestHelper.Session;
import com.interval.apibackend.support.MutableClock;
import com.interval.apibackend.support.TestClockConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * Pembersihan refresh token terhadap MySQL asli (butuh Docker).
 * Masa berlaku refresh token 30 hari, masa simpan setelah kedaluwarsa 7 hari: baris dibuang setelah hari ke-37.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestClockConfig.class)
@TestPropertySource(properties = "interval.scheduling.enabled=false")
class RefreshTokenCleanupIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RefreshTokenCleanupService cleanupService;

    @Autowired
    private RefreshTokenRepository tokenRepository;

    @BeforeEach
    void resetClock() {
        clock.set(TestClockConfig.BASE);
    }

    @Test
    void expiredTokens_areRemovedOnlyAfterTheRetentionPeriod() throws Exception {
        Session session = AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");
        String hash = TokenUtils.sha256Hex(session.refreshToken());
        assertThat(tokenRepository.findByTokenHash(hash)).isPresent();

        clock.set(TestClockConfig.BASE.plus(Duration.ofDays(36)));   // kedaluwarsa 6 hari lalu: masih dalam masa simpan
        cleanupService.purgeExpired();
        assertThat(tokenRepository.findByTokenHash(hash)).isPresent();

        clock.set(TestClockConfig.BASE.plus(Duration.ofDays(38)));   // kedaluwarsa 8 hari lalu: dibuang
        assertThat(cleanupService.purgeExpired()).isGreaterThanOrEqualTo(1);
        assertThat(tokenRepository.findByTokenHash(hash)).isEmpty();
    }

    @Test
    void revokedButUnexpiredTokens_areKept() throws Exception {
        Session session = AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");
        mvc.perform(post("/api/auth/logout").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + session.refreshToken() + "\"}"))
                .andExpect(status().isOk());

        clock.set(TestClockConfig.BASE.plus(Duration.ofDays(10)));
        cleanupService.purgeExpired();

        assertThat(tokenRepository.findByTokenHash(TokenUtils.sha256Hex(session.refreshToken())))
                .hasValueSatisfying(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void reuseDetection_stillWorksAfterACleanupRun() throws Exception {
        Session session = AuthTestHelper.registerAndLogin(mvc, "Asia/Jakarta");
        String rotated = JsonPath.read(mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + session.refreshToken() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.refreshToken");

        clock.set(TestClockConfig.BASE.plus(Duration.ofDays(10)));
        cleanupService.purgeExpired();

        // Token lama (sudah diputar) dipakai lagi setelah pembersihan: tetap terdeteksi dan semua sesi dicabut.
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + session.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(tokenRepository.findByTokenHash(TokenUtils.sha256Hex(rotated)))
                .hasValueSatisfying(token -> assertThat(token.getRevokedAt()).isNotNull());
    }
}
