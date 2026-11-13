package com.interval.apibackend.user.dto;

import com.interval.apibackend.common.validation.ValidTimezone;

/**
 * Semua field opsional (PATCH). Null = tidak diubah.
 * Aturan fullName (tidak kosong, 2-100 karakter setelah di-trim) dicek di UserService
 * agar pesannya deterministik. Email sengaja tidak ada di sini.
 */
public record UpdateProfileRequest(
        String fullName,

        @ValidTimezone
        String timezone
) {
}
