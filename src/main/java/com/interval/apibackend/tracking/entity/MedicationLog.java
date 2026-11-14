package com.interval.apibackend.tracking.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.interval.apibackend.common.entity.AuditableEntity;
import com.interval.apibackend.schedule.entity.DoseUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Satu baris = satu kejadian minum obat (occurrence).
 *
 * Semua relasi berupa kolom UUID biasa (bukan @ManyToOne) dan nullable, karena di database
 * medication_id / schedule_id / schedule_time_id bersifat ON DELETE SET NULL: riwayat tidak boleh hilang
 * saat obat/jadwal/jam dihapus. medicationName dan dose* adalah snapshot saat log dibuat.
 * scheduledAt dan actionAt dalam UTC.
 */
@Entity
@Table(name = "medication_logs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationLog extends AuditableEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "medication_id", length = 36)
    private UUID medicationId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "schedule_id", length = 36)
    private UUID scheduleId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "schedule_time_id", length = 36)
    private UUID scheduleTimeId;

    @Column(name = "medication_name", nullable = false, length = 150)
    private String medicationName;

    @Column(name = "dose_quantity", nullable = false, precision = 8, scale = 2)
    private BigDecimal doseQuantity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "dose_unit", nullable = false, length = 20)
    private DoseUnit doseUnit;

    @Column(name = "scheduled_at", nullable = false, updatable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private LogStatus status = LogStatus.PENDING;

    @Column(name = "action_at")
    private Instant actionAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public static MedicationLog pending(UUID userId, UUID medicationId, UUID scheduleId, UUID scheduleTimeId,
                                        String medicationName, BigDecimal doseQuantity, DoseUnit doseUnit,
                                        Instant scheduledAt) {
        MedicationLog log = new MedicationLog();
        log.userId = userId;
        log.medicationId = medicationId;
        log.scheduleId = scheduleId;
        log.scheduleTimeId = scheduleTimeId;
        log.medicationName = medicationName;
        log.doseQuantity = doseQuantity;
        log.doseUnit = doseUnit;
        log.scheduledAt = scheduledAt;
        return log;
    }
}
