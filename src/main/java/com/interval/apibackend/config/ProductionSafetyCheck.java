package com.interval.apibackend.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.interval.apibackend.security.JwtProperties;

import jakarta.annotation.PostConstruct;

/**
 * Menggagalkan startup profil "prod" jika konfigurasinya tidak aman, daripada berjalan diam-diam:
 *  - secret JWT harus >= 32 byte dan bukan nilai bawaan pengembangan;
 *  - FCM harus aktif (jika tidak, notifikasi hanya dicatat dan tidak pernah sampai ke perangkat,
 *    padahal riwayat menunjukkan SENT).
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    private final JwtProperties jwtProperties;
    private final boolean fcmEnabled;

    public ProductionSafetyCheck(JwtProperties jwtProperties,
                                 @Value("${interval.notification.fcm.enabled:false}") boolean fcmEnabled) {
        this.jwtProperties = jwtProperties;
        this.fcmEnabled = fcmEnabled;
    }

    @PostConstruct
    void verify() {
        validate(jwtProperties.secret(), fcmEnabled);
    }

    public static void validate(String jwtSecret, boolean fcmEnabled) {
        List<String> problems = new ArrayList<>();

        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            problems.add("interval.jwt.secret (JWT_SECRET) must be at least 32 bytes");
        } else {
            String lower = jwtSecret.toLowerCase(Locale.ROOT);
            if (lower.contains("dev-only") || lower.contains("change-me")) {
                problems.add("interval.jwt.secret (JWT_SECRET) is still a development placeholder");
            }
        }
        if (!fcmEnabled) {
            problems.add("interval.notification.fcm.enabled must be true (push would never be delivered)");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", problems));
        }
    }
}
