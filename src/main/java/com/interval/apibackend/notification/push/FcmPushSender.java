package com.interval.apibackend.notification.push;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

import lombok.extern.slf4j.Slf4j;

/**
 * Pengirim FCM sungguhan. Aktif hanya jika interval.notification.fcm.enabled=true.
 * Berkas kredensial service account dibaca dari interval.notification.fcm.credentials-path
 * (jangan pernah di-commit ke repository).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "interval.notification.fcm", name = "enabled", havingValue = "true")
public class FcmPushSender implements PushSender {

    private final FirebaseMessaging messaging;

    public FcmPushSender(@Value("${interval.notification.fcm.credentials-path}") String credentialsPath)
            throws IOException {
        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(loadCredentials(credentialsPath)).build())
                : FirebaseApp.getInstance();
        this.messaging = FirebaseMessaging.getInstance(app);
        log.info("FCM push sender initialized");
    }

    private static GoogleCredentials loadCredentials(String path) throws IOException {
        try (InputStream in = new FileInputStream(path)) {
            return GoogleCredentials.fromStream(in);
        }
    }

    @Override
    public PushResult send(PushMessage message) {
        Message fcmMessage = Message.builder()
                .setToken(message.token())
                .setNotification(Notification.builder().setTitle(message.title()).setBody(message.body()).build())
                .putAllData(message.data())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(message.sound()
                                ? AndroidNotification.builder().setSound("default").build()
                                : AndroidNotification.builder().build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(message.sound() ? Aps.builder().setSound("default").build() : Aps.builder().build())
                        .build())
                .build();
        try {
            messaging.send(fcmMessage);
            return PushResult.sent();
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            String error = code + ": " + e.getMessage();
            // Hanya kode yang pasti berarti "token ini tidak akan pernah berhasil" yang menonaktifkan perangkat.
            // INVALID_ARGUMENT sengaja TIDAK termasuk: bisa berarti pesan kita yang salah, bukan tokennya.
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
                return PushResult.invalidToken(error);
            }
            return PushResult.failed(error);
        }
    }
}
