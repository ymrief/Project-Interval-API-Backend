package com.interval.apibackend.notification.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.common.exception.BusinessRuleException;
import com.interval.apibackend.notification.dto.PreferencesResponse;
import com.interval.apibackend.notification.dto.UpdatePreferencesRequest;
import com.interval.apibackend.notification.entity.NotificationPreference;
import com.interval.apibackend.notification.repository.NotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    public static final int MAX_REMINDER_MINUTES = 1440;

    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional
    public PreferencesResponse get(UUID userId) {
        return PreferencesResponse.from(getOrCreate(userId));
    }

    @Transactional
    public PreferencesResponse update(UUID userId, UpdatePreferencesRequest request) {
        Integer minutes = request.reminderBeforeMinutes();
        if (minutes != null && (minutes < 0 || minutes > MAX_REMINDER_MINUTES)) {
            throw new BusinessRuleException("Invalid notification preferences", "reminderBeforeMinutes",
                    "Value must be between 0 and " + MAX_REMINDER_MINUTES);
        }

        NotificationPreference preference = getOrCreate(userId);
        if (request.notificationsEnabled() != null) {
            preference.setNotificationsEnabled(request.notificationsEnabled());
        }
        if (request.soundEnabled() != null) {
            preference.setSoundEnabled(request.soundEnabled());
        }
        if (minutes != null) {
            preference.setReminderBeforeMinutes(minutes);
        }
        return PreferencesResponse.from(preference);
    }

    /** Registrasi membuat baris default; untuk data lama yang belum punya, dibuat saat pertama dibaca. */
    private NotificationPreference getOrCreate(UUID userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> preferenceRepository.save(NotificationPreference.defaultsFor(userId)));
    }
}
