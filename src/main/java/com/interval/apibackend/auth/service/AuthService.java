package com.interval.apibackend.auth.service;

import java.util.Locale;
import java.util.UUID;

import com.interval.apibackend.auth.dto.*;
import com.interval.apibackend.common.exception.ConflictException;
import com.interval.apibackend.common.exception.ForbiddenException;
import com.interval.apibackend.common.exception.UnauthorizedException;
import com.interval.apibackend.notification.entity.NotificationPreference;
import com.interval.apibackend.notification.repository.NotificationPreferenceRepository;
import com.interval.apibackend.security.PasswordPolicy;
import com.interval.apibackend.user.entity.User;
import com.interval.apibackend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DEFAULT_TIMEZONE = "Asia/Jakarta";

    private final UserRepository userRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;

    /** Hash tiruan agar login dengan email tidak dikenal sama lambatnya dengan password salah. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       NotificationPreferenceRepository preferenceRepository,
                       TokenService tokenService,
                       PasswordEncoder passwordEncoder,
                       LoginRateLimiter loginRateLimiter) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        PasswordPolicy.requireValid("password", request.password());

        String email = normalizeEmail(request.email());
        String timezone = request.timezone() != null ? request.timezone() : DEFAULT_TIMEZONE;

        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }

        User user = User.create(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), timezone);
        try {
            // flush sekarang agar pelanggaran UNIQUE (dua register bersamaan) terdeteksi di sini
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }

        preferenceRepository.save(NotificationPreference.defaultsFor(user.getId()));

        return new RegisterResponse(user.getId(), user.getFullName(), user.getEmail(), user.getTimezone());
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());

        // Diperiksa SEBELUM verifikasi password: yang diblokir tidak mendapat kesempatan mencoba, tidak membuang
        // CPU BCrypt, dan jawabannya sama untuk email terdaftar maupun tidak (tidak membocorkan keberadaan akun).
        loginRateLimiter.checkAllowed(email, clientIp);

        User user = userRepository.findByEmail(email).orElse(null);

        // Selalu jalankan satu verifikasi BCrypt, walau user tidak ada (anti user-enumeration via timing).
        boolean passwordOk = !PasswordPolicy.exceedsMaxBytes(request.password())
                && passwordEncoder.matches(request.password(),
                user != null ? user.getPasswordHash() : dummyPasswordHash);

        if (user == null || !passwordOk) {
            loginRateLimiter.recordFailure(email, clientIp);
            throw new UnauthorizedException("Email or password is incorrect");
        }
        loginRateLimiter.recordSuccess(email);
        if (!user.isActive()) {
            throw new ForbiddenException("Account is deactivated");
        }

        TokenService.TokenPair pair = tokenService.issueTokens(user.getId());
        return LoginResponse.of(pair, user);
    }

    // Tidak @Transactional: lihat penjelasan noRollbackFor di TokenService.refresh.
    public TokenResponse refresh(RefreshRequest request) {
        return TokenResponse.from(tokenService.refresh(request.refreshToken()));
    }

    public void logout(UUID userId, LogoutRequest request) {
        tokenService.revoke(userId, request.refreshToken());
    }

    public void logoutAll(UUID userId) {
        tokenService.revokeAll(userId);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ConflictException emailTaken() {
        return new ConflictException("Email is already registered", "email", "Email already exists");
    }
}
