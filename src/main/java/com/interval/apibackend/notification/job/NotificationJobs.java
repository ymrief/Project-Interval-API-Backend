package com.interval.apibackend.notification.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.interval.apibackend.notification.service.NotificationDispatchService;

import lombok.RequiredArgsConstructor;

/**
 * Dimatikan dengan interval.scheduling.enabled=false (mis. di test).
 * Aman dijalankan di beberapa instance: uq_notification_once mencegah kiriman ganda.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "interval.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationJobs {

    private final NotificationDispatchService dispatchService;

    @Scheduled(initialDelayString = "${interval.notification.dispatch-initial-delay:PT45S}",
            fixedDelayString = "${interval.notification.dispatch-interval:PT1M}")
    public void dispatchDueNotifications() {
        dispatchService.dispatchDue();
    }
}
