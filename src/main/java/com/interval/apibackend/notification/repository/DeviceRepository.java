package com.interval.apibackend.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.interval.apibackend.notification.entity.Device;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByFcmToken(String fcmToken);

    /** Milik pengguna lain diperlakukan sama dengan tidak ada (404). */
    Optional<Device> findByIdAndUserId(UUID id, UUID userId);

    List<Device> findByUserIdAndActiveTrue(UUID userId);
}
