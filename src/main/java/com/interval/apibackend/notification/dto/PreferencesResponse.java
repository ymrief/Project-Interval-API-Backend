package com.interval.apibackend.notification.dto;

import com.interval.apibackend.notification.entity.NotificationPreference;

public record PreferencesResponse(boolean notificationsEnabled, boolean soundEnabled, int reminderBeforeMinutes) {

    public static PreferencesResponse from(NotificationPreference preference) {
        return new PreferencesResponse(preference.isNotificationsEnabled(), preference.isSoundEnabled(),
                preference.getReminderBeforeMinutes());
    }
}
