package com.interval.apibackend.notification.dto;

import com.interval.apibackend.notification.entity.DevicePlatform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank(message = "FCM token is required")
        @Size(max = 512, message = "FCM token must be at most 512 characters")
        String fcmToken,

        // Nilai di luar enum ditolak 422 oleh GlobalExceptionHandler ("Value must be one of: ANDROID, IOS, WEB").
        @NotNull(message = "Platform is required")
        DevicePlatform platform,

        @Size(max = 100, message = "Device name must be at most 100 characters")
        String deviceName
) {
}
