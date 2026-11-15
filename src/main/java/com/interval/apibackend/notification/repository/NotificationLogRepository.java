package com.interval.apibackend.notification.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.interval.apibackend.notification.entity.DeliveryStatus;
import com.interval.apibackend.notification.entity.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByUserId(UUID userId, Pageable pageable);

    Page<NotificationLog> findByUserIdAndDeliveryStatus(UUID userId, DeliveryStatus status, Pageable pageable);
}
