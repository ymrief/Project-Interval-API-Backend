package com.interval.apibackend.schedule.entity;

import java.time.LocalTime;

import com.interval.apibackend.common.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tabel schedule_times hanya punya created_at, jadi cukup extends BaseEntity.
 * Kolom day_key (generated) sengaja tidak dipetakan.
 */
@Entity
@Table(name = "schedule_times")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleTime extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
    private MedicationSchedule schedule;

    @Column(name = "scheduled_time", nullable = false, updatable = false)
    private LocalTime scheduledTime;

    // Kolom TINYINT; tanpa @JdbcTypeCode, Integer dipetakan ke INTEGER dan validate gagal.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "day_of_week", updatable = false)
    private Integer dayOfWeek;

    public static ScheduleTime of(MedicationSchedule schedule, LocalTime scheduledTime, Integer dayOfWeek) {
        ScheduleTime time = new ScheduleTime();
        time.schedule = schedule;
        time.scheduledTime = scheduledTime;
        time.dayOfWeek = dayOfWeek;
        return time;
    }
}
