package com.interval.apibackend.notification.repository;

import java.util.Optional;
import java.util.UUID;

import com.interval.apibackend.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    Optional<NotificationPreference> findByUserId(UUID userId);
}
