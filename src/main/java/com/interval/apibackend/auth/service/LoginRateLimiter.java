package com.interval.apibackend.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import com.interval.apibackend.auth.service.LoginRateLimitProperties;
import org.springframework.stereotype.Component;

import com.interval.apibackend.common.exception.TooManyRequestsException;

import lombok.extern.slf4j.Slf4j;

/**
 * Pembatas percobaan login GAGAL, per email dan per alamat IP (jendela tetap).
 *
 *  - Hanya kegagalan yang dihitung, jadi pengguna sah yang berhasil login tidak pernah terhambat.
 *  - Jendela dimulai pada kegagalan pertama; setelah mencapai batas, kunci diblokir sampai jendela berakhir.
 *  - Login berhasil mengosongkan hitungan email (bukan hitungan IP).
 *  - Kunci email dihitung untuk email APA PUN, terdaftar atau tidak, sehingga pemblokiran tidak membocorkan
 *    apakah sebuah email terdaftar.
 *  - checkAllowed dipanggil SEBELUM password diverifikasi, jadi penyerang yang diblokir tidak mendapat
 *    kesempatan mencoba dan tidak bisa membuang CPU BCrypt.
 *
 * Penyimpanan di memori (per instance): dengan N instance batas efektifnya sampai N kali lipat, dan hitungan
 * hilang saat restart. Jika perlu ketat lintas instance, ganti penyimpanannya dengan Redis atau tabel database.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private static final int MAX_TRACKED_KEYS = 50_000;

    private record Window(Instant start, int failures) {
    }

    private final LoginRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> byEmail = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> byIp = new ConcurrentHashMap<>();

    public LoginRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Melempar TooManyRequestsException (429) jika email atau IP sedang diblokir. */
    public void checkAllowed(String email, String clientIp) {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        long retryAfter = Math.max(
                retryAfterSeconds(byEmail.get(email), properties.maxFailuresPerEmail(), now),
                retryAfterSeconds(byIp.get(clientIp), properties.maxFailuresPerIp(), now));
        if (retryAfter > 0) {
            log.warn("Login blocked by rate limit (ip={}, retryAfter={}s)", clientIp, retryAfter);
            throw new TooManyRequestsException("Too many login attempts. Please try again later", retryAfter);
        }
    }

    public void recordFailure(String email, String clientIp) {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        increment(byEmail, email, now);
        increment(byIp, clientIp, now);
    }

    public void recordSuccess(String email) {
        byEmail.remove(email);
    }

    /** Membuang jendela yang sudah berakhir. Dipanggil job berkala agar memori tidak terus bertambah. */
    public int purgeExpired() {
        Instant now = clock.instant();
        int before = byEmail.size() + byIp.size();
        byEmail.values().removeIf(window -> isExpired(window, now));
        byIp.values().removeIf(window -> isExpired(window, now));
        return before - (byEmail.size() + byIp.size());
    }

    /** Jumlah kunci yang sedang dilacak (untuk pengujian dan pemantauan). */
    public int trackedKeys() {
        return byEmail.size() + byIp.size();
    }

    /** Mengosongkan semua hitungan. Dipakai test agar antar-test tidak saling memblokir. */
    public void clear() {
        byEmail.clear();
        byIp.clear();
    }

    // ------------------------------------------------------------------ internal

    private void increment(ConcurrentHashMap<String, Window> map, String key, Instant now) {
        if (map.size() >= MAX_TRACKED_KEYS) {
            purgeExpired();     // batas pengaman memori terhadap banjir email/IP acak
        }
        map.compute(key, (k, window) -> window == null || isExpired(window, now)
                ? new Window(now, 1)
                : new Window(window.start(), window.failures() + 1));
    }

    private boolean isExpired(Window window, Instant now) {
        return !now.isBefore(window.start().plus(properties.window()));
    }

    /** Sisa detik pemblokiran (dibulatkan ke atas, minimal 1), atau 0 jika tidak sedang diblokir. */
    private long retryAfterSeconds(Window window, int maxFailures, Instant now) {
        if (window == null || isExpired(window, now) || window.failures() < maxFailures) {
            return 0;
        }
        Duration remaining = Duration.between(now, window.start().plus(properties.window()));
        long seconds = remaining.toSeconds() + (remaining.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
