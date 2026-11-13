package com.interval.apibackend.user.event;

import java.util.UUID;

/**
 * Dipublikasikan UserService saat timezone pengguna benar-benar berubah.
 *
 * Jam pada jadwal dibaca dalam timezone pengguna, jadi occurrence PENDING mendatang
 * harus dibuat ulang. Itu tugas modul Schedule (occurrence generator), yang nanti
 * mendengarkan event ini dengan @TransactionalEventListener(phase = AFTER_COMMIT).
 * Modul User sengaja tidak mengenal modul Schedule.
 */
public record UserTimezoneChangedEvent(UUID userId, String oldTimezone, String newTimezone) {
}
