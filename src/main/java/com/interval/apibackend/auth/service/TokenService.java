package com.interval.apibackend.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.interval.apibackend.auth.entity.RefreshToken;
import com.interval.apibackend.auth.repository.RefreshTokenRepository;
import com.interval.apibackend.common.exception.UnauthorizedException;
import com.interval.apibackend.security.JwtProperties;
import com.interval.apibackend.security.JwtService;
import com.interval.apibackend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seluruh siklus token: terbit, rotasi, cabut. Dipakai juga oleh modul User (ganti password).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String INVALID_REFRESH_TOKEN = "Refresh token is invalid or expired";

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
    }

    private record IssuedRefreshToken(RefreshToken entity, String raw) {
    }

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public TokenPair issueTokens(UUID userId) {
        IssuedRefreshToken refresh = createRefreshToken(userId);
        return new TokenPair(jwtService.generateAccessToken(userId), refresh.raw(), jwtService.accessTokenTtlSeconds());
    }

    /**
     * Rotasi: token lama dicabut dan menunjuk token baru (replaced_by_token_id).
     *
     * noRollbackFor wajib: pada deteksi pemakaian ulang kita mencabut semua token lalu melempar 401.
     * Tanpa ini pencabutan ikut di-rollback. Karena itu pemanggil (AuthService.refresh)
     * TIDAK boleh membungkus pemanggilan ini dengan @Transactional.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenPair refresh(String rawRefreshToken) {
        Instant now = clock.instant();

        RefreshToken token = refreshTokenRepository
                .findByTokenHashForUpdate(TokenUtils.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN));

        if (token.getRevokedAt() != null) {
            // Token yang sudah DIROTASI lalu dipakai lagi = indikasi token dicuri: cabut semua sesi.
            // Token yang dicabut lewat logout/logout-all cukup ditolak.
            if (token.getReplacedByTokenId() != null) {
                log.warn("Refresh token reuse detected, revoking all sessions for user {}", token.getUserId());
                refreshTokenRepository.revokeAllActiveByUserId(token.getUserId(), now);
            }
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        userRepository.findById(token.getUserId())
                .filter(user -> user.isActive())
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN));

        IssuedRefreshToken next = createRefreshToken(token.getUserId());
        token.setRevokedAt(now);
        token.setReplacedByTokenId(next.entity().getId());

        return new TokenPair(jwtService.generateAccessToken(token.getUserId()), next.raw(),
                jwtService.accessTokenTtlSeconds());
    }

    /** Idempotent: token tidak dikenal, milik orang lain, atau sudah dicabut tetap dianggap sukses. */
    @Transactional
    public void revoke(UUID userId, String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawRefreshToken))
                .filter(token -> token.getUserId().equals(userId))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(clock.instant()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, clock.instant());
    }

    private IssuedRefreshToken createRefreshToken(UUID userId) {
        String raw = TokenUtils.generateRefreshToken();
        RefreshToken entity = RefreshToken.issue(
                userId,
                TokenUtils.sha256Hex(raw),
                clock.instant().plus(jwtProperties.refreshTokenTtl()));
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(entity, raw);
    }
}
