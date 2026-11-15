package com.interval.apibackend.notification.push;

/**
 * Batas antara logika notifikasi dan penyedia push. Implementasi tidak boleh melempar untuk kegagalan
 * pengiriman biasa: kembalikan PushResult. (Dispatcher tetap menangkap RuntimeException sebagai FAILED.)
 */
public interface PushSender {

    PushResult send(PushMessage message);
}
