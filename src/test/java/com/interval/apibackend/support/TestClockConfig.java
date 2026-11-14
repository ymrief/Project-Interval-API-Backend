package com.interval.apibackend.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Mengganti Clock aplikasi dengan MutableClock untuk integration test yang bergantung pada waktu.
 * Pakai dengan @Import(TestClockConfig.class); "sekarang" awal = Kamis 12 Nov 2026 10:00 UTC (17:00 WIB).
 *
 * Perhatian: JWT dan refresh token memakai Clock yang sama. Setelah jam dimajukan lebih dari 15 menit,
 * access token lama kedaluwarsa; login ulang pada jam baru sebelum memanggil API lagi.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    public static final Instant BASE = Instant.parse("2026-11-12T10:00:00Z");

    // Nama bean berbeda dari "clock" (AppConfig) agar tidak bentrok; @Primary membuatnya dipakai semua bean.
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(BASE);
    }
}
