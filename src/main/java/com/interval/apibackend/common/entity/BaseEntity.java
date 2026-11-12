package com.interval.apibackend.common.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * Dasar semua entity: id UUID + created_at.
 *
 * - Kolom id di database bertipe CHAR(36). Tanpa @JdbcTypeCode(SqlTypes.CHAR),
 *   Hibernate memetakan UUID ke BINARY(16) dan ddl-auto=validate akan gagal.
 *   Aturan yang sama berlaku untuk kolom UUID biasa (bukan relasi) di entity lain.
 * - Waktu memakai Instant (UTC); lihat hibernate.jdbc.time_zone=UTC di application.properties.
 * - Tabel yang hanya punya created_at (schedule_times, refresh_tokens, notification_logs)
 *   cukup extends BaseEntity; tabel lain extends AuditableEntity.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // equals/hashCode berbasis id dan aman terhadap proxy Hibernate.
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        BaseEntity that = (BaseEntity) o;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
