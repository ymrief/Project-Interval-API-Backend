package com.interval.apibackend.notification.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.interval.apibackend.notification.entity.Device;
import com.interval.apibackend.notification.entity.DevicePlatform;

/** fcmToken sengaja tidak dikembalikan: itu kredensial perangkat. */
public record DeviceResponse(
        UUID id,
        DevicePlatform platform,
        String deviceName,
        @JsonProperty("isActive") boolean active
) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(device.getId(), device.getPlatform(), device.getDeviceName(), device.isActive());
    }
}
