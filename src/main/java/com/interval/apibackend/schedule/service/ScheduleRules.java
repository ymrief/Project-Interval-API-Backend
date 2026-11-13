package com.interval.apibackend.schedule.service;

import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.schedule.entity.FrequencyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semua aturan bisnis jadwal di satu tempat. Bekerja pada "draft" netral sehingga SAMA dipakai untuk
 * create (dari request) dan update (dari hasil gabungan data lama + field yang dikirim).
 *
 * Mengembalikan SEMUA error sekaligus (kunci = nama field, atau "times[i].field" untuk elemen jam).
 * Tidak melempar exception: pemanggil memutuskan (service menambah aturan lain, lalu melempar).
 */
public final class ScheduleRules {

    public static final int MAX_TIMES = 50;
    private static final BigDecimal MAX_DOSE = new BigDecimal("999999.99");

    public record TimeDraft(LocalTime scheduledTime, Integer dayOfWeek) {
    }

    public record ScheduleDraft(
            FrequencyType frequencyType,
            Integer intervalDays,
            BigDecimal doseQuantity,
            DoseUnit doseUnit,
            LocalDate startDate,
            LocalDate endDate,
            List<TimeDraft> times
    ) {
    }

    private ScheduleRules() {
    }

    public static Map<String, String> validate(ScheduleDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (draft.frequencyType() == null) {
            errors.put("frequencyType", "Frequency type is required");
        }
        validateDose(draft.doseQuantity(), errors);
        if (draft.doseUnit() == null) {
            errors.put("doseUnit", "Dose unit is required");
        }
        if (draft.startDate() == null) {
            errors.put("startDate", "Start date is required");
        }
        if (draft.startDate() != null && draft.endDate() != null && draft.endDate().isBefore(draft.startDate())) {
            errors.put("endDate", "End date must not be earlier than start date");
        }
        validateIntervalDays(draft, errors);
        validateTimes(draft, errors);
        return errors;
    }

    private static void validateDose(BigDecimal dose, Map<String, String> errors) {
        if (dose == null) {
            errors.put("doseQuantity", "Dose quantity is required");
        } else if (dose.signum() <= 0) {
            errors.put("doseQuantity", "Dose quantity must be greater than 0");
        } else if (dose.stripTrailingZeros().scale() > 2) {
            errors.put("doseQuantity", "Dose quantity must have at most 2 decimal places");
        } else if (dose.compareTo(MAX_DOSE) > 0) {
            errors.put("doseQuantity", "Dose quantity must be at most 999999.99");
        }
    }

    private static void validateIntervalDays(ScheduleDraft draft, Map<String, String> errors) {
        FrequencyType type = draft.frequencyType();
        Integer days = draft.intervalDays();
        if (type == FrequencyType.INTERVAL) {
            if (days == null || days < 1) {
                errors.put("intervalDays", "Interval days must be at least 1 for INTERVAL schedules");
            }
        } else if (type != null && days != null) {
            errors.put("intervalDays", "Interval days is only allowed for INTERVAL schedules");
        }
    }

    private static void validateTimes(ScheduleDraft draft, Map<String, String> errors) {
        List<TimeDraft> times = draft.times();
        if (times == null || times.isEmpty()) {
            errors.put("times", "At least one time is required");
            return;
        }
        if (times.size() > MAX_TIMES) {
            errors.put("times", "At most " + MAX_TIMES + " times are allowed");
            return;
        }

        FrequencyType type = draft.frequencyType();
        Set<TimeDraft> seen = new HashSet<>();
        boolean duplicate = false;

        for (int i = 0; i < times.size(); i++) {
            TimeDraft time = times.get(i);
            String prefix = "times[" + i + "]";

            if (time == null) {
                errors.put(prefix, "Time entry must not be null");
                continue;
            }
            if (time.scheduledTime() == null) {
                errors.put(prefix + ".scheduledTime", "Scheduled time is required");
            }

            Integer day = time.dayOfWeek();
            if (day != null && (day < 1 || day > 7)) {
                errors.put(prefix + ".dayOfWeek", "Day of week must be between 1 and 7");
            } else if (type == FrequencyType.WEEKLY && day == null) {
                errors.put(prefix + ".dayOfWeek", "Day of week is required for WEEKLY schedules");
            } else if (type != null && type != FrequencyType.WEEKLY && day != null) {
                errors.put(prefix + ".dayOfWeek", "Day of week must be empty for DAILY and INTERVAL schedules");
            }

            if (time.scheduledTime() != null && !seen.add(time)) {
                duplicate = true;
            }
        }
        if (duplicate) {
            errors.putIfAbsent("times", "Duplicate times are not allowed");
        }
    }
}
