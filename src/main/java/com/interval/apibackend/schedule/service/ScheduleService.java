package com.interval.apibackend.schedule.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.interval.apibackend.schedule.service.ScheduleRules.TimeDraft;
import com.interval.apibackend.schedule.service.ScheduleRules.ScheduleDraft;
import com.interval.apibackend.common.exception.BusinessRuleException;
import com.interval.apibackend.common.exception.NotFoundException;
import com.interval.apibackend.medication.entity.Medication;
import com.interval.apibackend.medication.repository.MedicationRepository;
import com.interval.apibackend.schedule.dto.CreateScheduleRequest;
import com.interval.apibackend.schedule.dto.ScheduleResponse;
import com.interval.apibackend.schedule.dto.ScheduleTimeRequest;
import com.interval.apibackend.schedule.dto.UpdateScheduleRequest;
import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.schedule.entity.FrequencyType;
import com.interval.apibackend.schedule.entity.MedicationSchedule;
import com.interval.apibackend.schedule.entity.ScheduleTime;
import com.interval.apibackend.schedule.event.ScheduleChangedEvent;
import com.interval.apibackend.schedule.event.ScheduleChangedEvent.Type;
import com.interval.apibackend.schedule.repository.MedicationScheduleRepository;
import com.interval.apibackend.schedule.repository.ScheduleSpecifications;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final String INVALID_CONFIGURATION = "Invalid schedule configuration";

    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationRepository medicationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** Identitas sebuah jam dalam jadwal; dipakai untuk membandingkan jam lama dengan jam baru. */
    private record TimeKey(Integer dayOfWeek, java.time.LocalTime time) {
    }

    // ------------------------------------------------------------------ query

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(UUID userId, UUID medicationId, Boolean active) {
        return scheduleRepository
                .findAll(ScheduleSpecifications.owned(userId, medicationId, active),
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))
                .stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(UUID userId, UUID scheduleId) {
        return ScheduleResponse.from(findOwned(userId, scheduleId));
    }

    // ----------------------------------------------------------------- create

    @Transactional
    public ScheduleResponse create(UUID userId, UUID medicationId, CreateScheduleRequest request) {
        Medication medication = medicationRepository
                .findByIdAndUserIdAndDeletedAtIsNull(medicationId, userId)
                .orElseThrow(() -> new NotFoundException("Medication not found"));

        List<ScheduleRules.TimeDraft> times = toDrafts(request.times());
        Map<String, String> errors = ScheduleRules.validate(new ScheduleRules.ScheduleDraft(
                request.frequencyType(), request.intervalDays(), request.doseQuantity(), request.doseUnit(),
                request.startDate(), request.endDate(), times));
        if (!medication.isActive()) {
            errors.put("medicationId", "Medication is not active");
        }
        failIfInvalid(errors);

        MedicationSchedule schedule = MedicationSchedule.create(
                medication,
                request.frequencyType(),
                request.frequencyType() == FrequencyType.INTERVAL ? request.intervalDays() : null,
                normalizeDose(request.doseQuantity()),
                request.doseUnit(),
                request.startDate(),
                request.endDate());
        for (TimeKey key : distinctKeys(times)) {
            schedule.addTime(ScheduleTime.of(schedule, key.time(), key.dayOfWeek()));
        }

        // flush agar id jadwal dan jam sudah terisi saat response dibentuk
        scheduleRepository.saveAndFlush(schedule);
        publish(userId, schedule, Type.CREATED);
        return ScheduleResponse.from(schedule);
    }

    // ----------------------------------------------------------------- update

    @Transactional
    public ScheduleResponse update(UUID userId, UUID scheduleId, UpdateScheduleRequest request) {
        MedicationSchedule schedule = findOwned(userId, scheduleId);

        FrequencyType oldType = schedule.getFrequencyType();
        FrequencyType newType = request.getFrequencyType() != null ? request.getFrequencyType() : oldType;

        // dayOfWeek hanya bermakna pada WEEKLY, jadi jam lama tidak bisa dipakai lintas batas WEEKLY.
        if (request.getTimes() == null && (newType == FrequencyType.WEEKLY) != (oldType == FrequencyType.WEEKLY)) {
            throw new BusinessRuleException(INVALID_CONFIGURATION, "times",
                    "Times must be provided when changing frequency type to or from WEEKLY");
        }

        // Gabungkan data lama dengan field yang dikirim, lalu validasi HASIL GABUNGAN.
        Integer intervalDays = request.isIntervalDaysPresent()
                ? request.getIntervalDays()
                : (newType == FrequencyType.INTERVAL ? schedule.getIntervalDays() : null);
        BigDecimal dose = request.getDoseQuantity() != null ? request.getDoseQuantity() : schedule.getDoseQuantity();
        DoseUnit unit = request.getDoseUnit() != null ? request.getDoseUnit() : schedule.getDoseUnit();
        LocalDate endDate = request.isEndDatePresent() ? request.getEndDate() : schedule.getEndDate();
        List<TimeDraft> times = request.getTimes() != null
                ? toDrafts(request.getTimes())
                : schedule.getTimes().stream()
                        .map(t -> new TimeDraft(t.getScheduledTime(), t.getDayOfWeek())).toList();

        Map<String, String> errors = ScheduleRules.validate(new ScheduleDraft(
                newType, intervalDays, dose, unit, schedule.getStartDate(), endDate, times));
        if (request.getStartDate() != null && !request.getStartDate().equals(schedule.getStartDate())) {
            errors.put("startDate", "Start date cannot be changed");
        }
        failIfInvalid(errors);

        // Terapkan, dan catat apakah ada yang benar-benar berubah.
        boolean changed = false;
        if (newType != oldType) {
            schedule.setFrequencyType(newType);
            changed = true;
        }
        if (!java.util.Objects.equals(intervalDays, schedule.getIntervalDays())) {
            schedule.setIntervalDays(intervalDays);
            changed = true;
        }
        BigDecimal normalizedDose = normalizeDose(dose);
        if (normalizedDose.compareTo(schedule.getDoseQuantity()) != 0) {
            schedule.setDoseQuantity(normalizedDose);
            changed = true;
        }
        if (unit != schedule.getDoseUnit()) {
            schedule.setDoseUnit(unit);
            changed = true;
        }
        if (!java.util.Objects.equals(endDate, schedule.getEndDate())) {
            schedule.setEndDate(endDate);
            changed = true;
        }
        if (request.getActive() != null && request.getActive() != schedule.isActive()) {
            schedule.setActive(request.getActive());
            changed = true;
        }
        if (syncTimes(schedule, times)) {
            changed = true;
        }

        if (!changed) {
            return ScheduleResponse.from(schedule);   // tanpa UPDATE dan tanpa event
        }
        scheduleRepository.saveAndFlush(schedule);
        publish(userId, schedule, Type.UPDATED);
        return ScheduleResponse.from(schedule);
    }

    // ----------------------------------------------------------------- delete

    /** Soft delete: riwayat konsumsi tetap utuh; occurrence PENDING mendatang dibatalkan oleh listener. */
    @Transactional
    public void delete(UUID userId, UUID scheduleId) {
        MedicationSchedule schedule = findOwned(userId, scheduleId);
        schedule.setDeletedAt(clock.instant());
        schedule.setActive(false);
        publish(userId, schedule, Type.DELETED);
    }

    // ----------------------------------------------------------------- helper

    private MedicationSchedule findOwned(UUID userId, UUID scheduleId) {
        // Milik orang lain, sudah dihapus, atau tidak ada: semuanya 404 yang sama.
        return scheduleRepository
                .findOne(ScheduleSpecifications.owned(userId, null, null).and(ScheduleSpecifications.withId(scheduleId)))
                .orElseThrow(() -> new NotFoundException("Schedule not found"));
    }

    /**
     * Menyamakan jam di database dengan jam yang diinginkan TANPA menghapus-membuat ulang semuanya:
     * baris yang sama persis (hari + jam) dipertahankan beserta id-nya.
     * Hanya baris yang tidak lagi diinginkan dihapus (orphanRemoval) dan baris baru ditambahkan.
     * Karena itu unique (schedule_id, day_key, scheduled_time) tidak pernah bentrok walau Hibernate
     * menjalankan INSERT sebelum DELETE.
     */
    private boolean syncTimes(MedicationSchedule schedule, List<TimeDraft> desired) {
        Set<TimeKey> desiredKeys = distinctKeys(desired);
        Set<TimeKey> existingKeys = new HashSet<>();
        boolean changed = false;

        for (Iterator<ScheduleTime> it = schedule.getTimes().iterator(); it.hasNext(); ) {
            ScheduleTime time = it.next();
            TimeKey key = new TimeKey(time.getDayOfWeek(), time.getScheduledTime());
            if (desiredKeys.contains(key)) {
                existingKeys.add(key);
            } else {
                it.remove();
                changed = true;
            }
        }
        for (TimeKey key : desiredKeys) {
            if (!existingKeys.contains(key)) {
                schedule.addTime(ScheduleTime.of(schedule, key.time(), key.dayOfWeek()));
                changed = true;
            }
        }
        return changed;
    }

    private static Set<TimeKey> distinctKeys(List<TimeDraft> drafts) {
        Set<TimeKey> keys = new LinkedHashSet<>();
        for (TimeDraft draft : drafts) {
            keys.add(new TimeKey(draft.dayOfWeek(), draft.scheduledTime()));
        }
        return keys;
    }

    /** Request -> draft. Detik dipotong agar "08:00:00.500" dan "08:00:00" tidak dianggap berbeda. */
    private static List<TimeDraft> toDrafts(List<ScheduleTimeRequest> requests) {
        if (requests == null) {
            return null;
        }
        return requests.stream()
                .map(r -> r == null ? null : new TimeDraft(
                        r.scheduledTime() == null ? null : r.scheduledTime().truncatedTo(ChronoUnit.SECONDS),
                        r.dayOfWeek()))
                .toList();
    }

    private static BigDecimal normalizeDose(BigDecimal dose) {
        return dose.setScale(2, RoundingMode.UNNECESSARY);   // aman: ScheduleRules menjamin maksimal 2 desimal
    }

    private static void failIfInvalid(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new BusinessRuleException(INVALID_CONFIGURATION, errors);
        }
    }

    private void publish(UUID userId, MedicationSchedule schedule, Type type) {
        eventPublisher.publishEvent(
                new ScheduleChangedEvent(userId, schedule.getMedication().getId(), schedule.getId(), type));
    }
}
