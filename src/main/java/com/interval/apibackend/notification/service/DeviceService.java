package com.interval.apibackend.notification.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.common.exception.ConflictException;
import com.interval.apibackend.common.exception.NotFoundException;
import com.interval.apibackend.notification.dto.DeviceResponse;
import com.interval.apibackend.notification.dto.RegisterDeviceRequest;
import com.interval.apibackend.notification.entity.Device;
import com.interval.apibackend.notification.repository.DeviceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceService {

    public record Registration(DeviceResponse device, boolean created) {
    }

    private final DeviceRepository deviceRepository;
    private final Clock clock;

    /**
     * Upsert berdasarkan token FCM (unik secara global). Token yang sudah terdaftar dipindahkan ke pengguna
     * saat ini, diaktifkan kembali, dan lastSeenAt diperbarui: satu perangkat fisik bisa berganti akun,
     * dan token yang sempat dinonaktifkan karena UNREGISTERED hidup lagi saat aplikasi mendaftar ulang.
     */
    @Transactional
    public Registration register(UUID userId, RegisterDeviceRequest request) {
        String token = request.fcmToken().trim();
        Optional<Device> existing = deviceRepository.findByFcmToken(token);

        if (existing.isPresent()) {
            Device device = existing.get();
            device.setUserId(userId);
            device.setActive(true);
            device.setPlatform(request.platform());
            if (request.deviceName() != null) {
                device.setDeviceName(blankToNull(request.deviceName()));
            }
            device.setLastSeenAt(clock.instant());
            deviceRepository.saveAndFlush(device);
            return new Registration(DeviceResponse.from(device), false);
        }

        Device device = Device.create(userId, token, request.platform(), blankToNull(request.deviceName()),
                clock.instant());
        try {
            deviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException e) {
            // Dua pendaftaran token yang sama bersamaan; yang kalah cukup mengulang.
            throw new ConflictException("Device is being registered concurrently, please retry");
        }
        return new Registration(DeviceResponse.from(device), true);
    }

    /** Dipanggil client saat logout. Hard delete aman: notification_logs.device_id ON DELETE SET NULL. */
    @Transactional
    public void delete(UUID userId, UUID deviceId) {
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new NotFoundException("Device not found"));
        deviceRepository.delete(device);
    }

    /** Dipanggil dispatcher saat FCM menyatakan token tidak valid lagi. */
    @Transactional
    public void deactivate(UUID deviceId) {
        deviceRepository.findById(deviceId).ifPresent(device -> device.setActive(false));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
