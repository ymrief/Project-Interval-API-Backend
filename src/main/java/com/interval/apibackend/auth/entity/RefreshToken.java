package com.interval.apibackend.auth.entity;

import java.time.Instant;
import java.util.UUID;

import com.interval.apibackend.common.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tabel refresh_tokens hanya punya created_at, sehingga cukup extends BaseEntity. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private UUID userId;

    // SHA-256 hex = 64 karakter; kolom CHAR(64) (tanpa anotasi ini validate akan menuntut VARCHAR).
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Setter
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Setter
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "replaced_by_token_id", length = 36)
    private UUID replacedByTokenId;

    public static RefreshToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }
}
