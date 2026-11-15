package com.interval.apibackend.notification.dto;

/**
 * Semua field opsional (PATCH); null = tidak diubah. Tidak ada field nullable di sini, jadi record cukup.
 * Rentang reminderBeforeMinutes (0-1440) dicek di service agar pesannya sesuai spec
 * ("Invalid notification preferences").
 */
public record UpdatePreferencesRequest(Boolean notificationsEnabled, Boolean soundEnabled, Integer reminderBeforeMinutes) {
}
