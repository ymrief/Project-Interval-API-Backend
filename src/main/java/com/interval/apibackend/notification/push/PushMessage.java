package com.interval.apibackend.notification.push;

import java.util.Map;

public record PushMessage(
        String token,
        String title,
        String body,
        boolean sound,
        Map<String, String> data
) {
}
