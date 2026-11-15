package com.interval.apibackend.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.interval.apibackend.notification.push.PushMessage;
import com.interval.apibackend.notification.push.PushResult;
import com.interval.apibackend.notification.push.PushSender;

/** Pengganti FCM untuk test: merekam semua pesan dan hasilnya bisa diskenariokan per token. */
public class FakePushSender implements PushSender {

    private final List<PushMessage> sent = new CopyOnWriteArrayList<>();
    private final Map<String, PushResult> scripted = new ConcurrentHashMap<>();

    @Override
    public PushResult send(PushMessage message) {
        sent.add(message);
        return scripted.getOrDefault(message.token(), PushResult.sent());
    }

    public void invalidate(String token) {
        scripted.put(token, PushResult.invalidToken("UNREGISTERED"));
    }

    public void fail(String token, String error) {
        scripted.put(token, PushResult.failed(error));
    }

    /** Dispatcher memproses SEMUA pengguna di database; test selalu menyaring berdasarkan token miliknya. */
    public List<PushMessage> sentTo(String token) {
        return sent.stream().filter(message -> message.token().equals(token)).toList();
    }

    public void reset() {
        sent.clear();
        scripted.clear();
    }
}
