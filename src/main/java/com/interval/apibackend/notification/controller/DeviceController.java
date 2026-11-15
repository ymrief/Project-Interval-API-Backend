package com.interval.apibackend.notification.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interval.apibackend.common.response.ApiResponse;
import com.interval.apibackend.notification.dto.DeviceResponse;
import com.interval.apibackend.notification.dto.RegisterDeviceRequest;
import com.interval.apibackend.notification.service.DeviceService;
import com.interval.apibackend.security.CurrentUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /** 201 untuk perangkat baru, 200 untuk token yang sudah terdaftar (upsert). */
    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> register(@Valid @RequestBody RegisterDeviceRequest request) {
        DeviceService.Registration registration = deviceService.register(CurrentUser.id(), request);
        if (registration.created()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Device registered successfully", registration.device()));
        }
        return ResponseEntity.ok(ApiResponse.success("Device updated successfully", registration.device()));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> delete(@PathVariable UUID deviceId) {
        deviceService.delete(CurrentUser.id(), deviceId);
        return ResponseEntity.noContent().build();
    }
}
