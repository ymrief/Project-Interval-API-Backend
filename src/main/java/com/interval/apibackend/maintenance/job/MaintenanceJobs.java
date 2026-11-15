package com.interval.apibackend.maintenance.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.interval.apibackend.auth.service.LoginRateLimiter;
import com.interval.apibackend.maintenance.service.RefreshTokenCleanupService;

import lombok.RequiredArgsConstructor;

/** Dimatikan dengan interval.scheduling.enabled=false. Keduanya idempotent dan aman di beberapa instance. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "interval.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceJobs {

    private final RefreshTokenCleanupService refreshTokenCleanupService;
    private final LoginRateLimiter loginRateLimiter;

    /** Setiap hari 03:30 UTC. */
    @Scheduled(cron = "${interval.maintenance.refresh-token-cleanup-cron:0 30 3 * * *}", zone = "UTC")
    public void purgeExpiredRefreshTokens() {
        refreshTokenCleanupService.purgeExpired();
    }

    @Scheduled(initialDelayString = "${interval.maintenance.rate-limit-purge-interval:PT5M}",
            fixedDelayString = "${interval.maintenance.rate-limit-purge-interval:PT5M}")
    public void purgeRateLimiter() {
        loginRateLimiter.purgeExpired();
    }
}
