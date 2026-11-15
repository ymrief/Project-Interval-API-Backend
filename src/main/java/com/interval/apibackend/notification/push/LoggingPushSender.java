package com.interval.apibackend.notification.push;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Pengganti saat FCM dimatikan (default, untuk pengembangan lokal): hanya mencatat log dan menganggap SENT.
 * PERINGATAN: tidak ada notifikasi yang benar-benar sampai ke perangkat. Di production wajib
 * interval.notification.fcm.enabled=true.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "interval.notification.fcm", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class LoggingPushSender implements PushSender {

    @PostConstruct
    void warn() {
        log.warn("FCM is DISABLED (interval.notification.fcm.enabled=false): push notifications are only logged, "
                + "NOT delivered to devices. Do not use this mode in production.");
    }

    @Override
    public PushResult send(PushMessage message) {
        String token = message.token();
        log.info("[push disabled] to token {}...: {} - {}",
                token.substring(0, Math.min(8, token.length())), message.title(), message.body());
        return PushResult.sent();
    }
}
