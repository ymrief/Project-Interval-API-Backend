package com.interval.apibackend.notification.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.notification.entity.NotificationLog;
import com.interval.apibackend.notification.entity.NotificationType;
import com.interval.apibackend.notification.repository.NotificationLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Operasi kecil bertransaksi sendiri yang dipakai dispatcher. Sengaja kelas terpisah: dispatcher TIDAK
 * transaksional (pengiriman jaringan tidak boleh menahan transaksi database).
 */
@Service
@RequiredArgsConstructor
public class NotificationLogService {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final NotificationLogRepository logRepository;

    /**
     * Mencatat QUEUED SEBELUM mengirim, di transaksi baru yang langsung commit. Jika proses lain (instance lain)
     * sudah mencatat kombinasi (log, perangkat, tipe) yang sama, uq_notification_once melempar
     * DataIntegrityViolationException dan pemanggil melewati pengiriman: tidak ada kiriman ganda.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID queue(UUID userId, UUID deviceId, UUID medicationLogId, NotificationType type) {
        return logRepository.saveAndFlush(NotificationLog.queued(userId, deviceId, medicationLogId, type)).getId();
    }

    @Transactional
    public void markSent(UUID notificationLogId, Instant sentAt) {
        logRepository.findById(notificationLogId).ifPresent(log -> log.markSent(sentAt));
    }

    @Transactional
    public void markFailed(UUID notificationLogId, String error) {
        String truncated = error == null ? null
                : error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        logRepository.findById(notificationLogId).ifPresent(log -> log.markFailed(truncated));
    }
}
