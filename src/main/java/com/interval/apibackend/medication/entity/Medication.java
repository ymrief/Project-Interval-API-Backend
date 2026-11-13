package com.interval.apibackend.medication.entity;

import java.time.Instant;
import java.util.UUID;

import com.interval.apibackend.common.entity.AuditableEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * isActive  = obat dijeda/aktif (pengingat berhenti jika false)
 * deletedAt = soft delete; obat yang sudah dihapus tidak pernah ditampilkan atau diubah.
 */
@Entity
@Table(name = "medications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication extends AuditableEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "dosage", length = 100)
    private String dosage;

    // Tanpa @JdbcTypeCode(VARCHAR), Hibernate 6.2+ di MySQL bisa mengharapkan kolom bertipe ENUM,
    // padahal kolomnya VARCHAR(20) + CHECK constraint, sehingga ddl-auto=validate gagal.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "dosage_form", nullable = false, length = 20)
    private DosageForm dosageForm = DosageForm.OTHER;

    // columnDefinition agar validasi skema cocok dengan kolom TEXT.
    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Medication create(UUID userId, String name, String dosage, DosageForm dosageForm,
                                    String instructions, String notes, String imageUrl) {
        Medication medication = new Medication();
        medication.userId = userId;
        medication.name = name;
        medication.dosage = dosage;
        medication.dosageForm = dosageForm;
        medication.instructions = instructions;
        medication.notes = notes;
        medication.imageUrl = imageUrl;
        return medication;
    }
}
