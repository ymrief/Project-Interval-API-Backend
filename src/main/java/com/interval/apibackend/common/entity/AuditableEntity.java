package com.interval.apibackend.common.entity;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/** BaseEntity + updated_at, untuk tabel yang punya kolom updated_at. */
@Getter
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
