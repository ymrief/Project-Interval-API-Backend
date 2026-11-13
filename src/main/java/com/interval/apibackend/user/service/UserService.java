package com.interval.apibackend.user.service;

import java.util.UUID;

import com.interval.apibackend.auth.dto.TokenResponse;
import com.interval.apibackend.auth.service.TokenService;
import com.interval.apibackend.common.exception.BusinessRuleException;
import com.interval.apibackend.common.exception.UnauthorizedException;
import com.interval.apibackend.security.PasswordPolicy;
import com.interval.apibackend.user.dto.ChangePasswordRequest;
import com.interval.apibackend.user.dto.UpdateProfileRequest;
import com.interval.apibackend.user.dto.UserProfileResponse;
import com.interval.apibackend.user.dto.UserSummary;
import com.interval.apibackend.user.entity.User;
import com.interval.apibackend.user.event.UserTimezoneChangedEvent;
import com.interval.apibackend.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String VALIDATION_FAILED = "Validation failed";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        return UserProfileResponse.from(getUser(userId));
    }

    @Transactional
    public UserSummary updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getUser(userId);

        if (request.fullName() != null) {
            user.setFullName(validatedFullName(request.fullName()));
        }

        if (request.timezone() != null && !request.timezone().equals(user.getTimezone())) {
            String oldTimezone = user.getTimezone();
            user.setTimezone(request.timezone());
            // Listener (modul Schedule) memakai AFTER_COMMIT, jadi tidak terpicu jika transaksi gagal.
            eventPublisher.publishEvent(new UserTimezoneChangedEvent(userId, oldTimezone, request.timezone()));
        }

        // Tidak perlu save(): entity yang dikelola otomatis di-UPDATE saat commit (dirty checking).
        return UserSummary.from(user);
    }

    /**
     * Ganti password, lalu cabut SEMUA sesi dan terbitkan token baru untuk perangkat ini.
     * Seluruhnya satu transaksi: password baru dan pencabutan sesi tersimpan bersama atau tidak sama sekali.
     */
    @Transactional
    public TokenResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = getUser(userId);

        boolean currentPasswordOk = !PasswordPolicy.exceedsMaxBytes(request.currentPassword())
                && passwordEncoder.matches(request.currentPassword(), user.getPasswordHash());
        if (!currentPasswordOk) {
            throw new BusinessRuleException("Current password is incorrect",
                    "currentPassword", "Password does not match");
        }

        PasswordPolicy.requireValid("newPassword", request.newPassword(),
                "New password must be between 8 and 72 characters");
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessRuleException(VALIDATION_FAILED,
                    "newPassword", "New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        // Urutan penting: revokeAll menjalankan UPDATE massal dengan flushAutomatically (hash baru
        // ditulis lebih dulu) dan clearAutomatically (entity 'user' lepas dari persistence context).
        // Setelah baris ini jangan mengubah 'user' lagi, perubahannya tidak akan tersimpan.
        tokenService.revokeAll(userId);
        return TokenResponse.from(tokenService.issueTokens(userId));
    }

    private User getUser(UUID userId) {
        // Token valid tetapi user sudah tidak ada -> perlakukan sebagai tidak terautentikasi.
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }

    private static String validatedFullName(String raw) {
        String name = raw.trim();
        if (name.isEmpty()) {
            throw new BusinessRuleException(VALIDATION_FAILED, "fullName", "Full name must not be blank");
        }
        if (name.length() < 2 || name.length() > 100) {
            throw new BusinessRuleException(VALIDATION_FAILED,
                    "fullName", "Full name must be between 2 and 100 characters");
        }
        return name;
    }
}
