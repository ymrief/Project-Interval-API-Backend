package com.interval.apibackend.schedule.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.interval.apibackend.common.entity.AuditableEntity;
import com.interval.apibackend.medication.entity.Medication;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Aturan jadwal (bukan kejadian). Kejadian nyata (occurrence) ada di medication_logs.
 * Jam dibaca dalam timezone pengguna; tidak ada kolom timezone di sini.
 * Untuk INTERVAL, startDate menjadi acuan penghitungan setiap intervalDays hari.
 */
@Entity
@Table(name = "medication_schedules")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationSchedule extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false, updatable = false)
    private Medication medication;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "frequency_type", nullable = false, length = 20)
    private FrequencyType frequencyType;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "dose_quantity", nullable = false, precision = 8, scale = 2)
    private BigDecimal doseQuantity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "dose_unit", nullable = false, length = 20)
    private DoseUnit doseUnit;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleTime> times = new ArrayList<>();

    public static MedicationSchedule create(Medication medication, FrequencyType frequencyType,
                                            Integer intervalDays, BigDecimal doseQuantity, DoseUnit doseUnit,
                                            LocalDate startDate, LocalDate endDate) {
        MedicationSchedule schedule = new MedicationSchedule();
        schedule.medication = medication;
        schedule.frequencyType = frequencyType;
        schedule.intervalDays = intervalDays;
        schedule.doseQuantity = doseQuantity;
        schedule.doseUnit = doseUnit;
        schedule.startDate = startDate;
        schedule.endDate = endDate;
        return schedule;
    }

    public void addTime(ScheduleTime time) {
        times.add(time);
    }
}
