package com.interval.apibackend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.schedule.service.ScheduleRules.ScheduleDraft;
import com.interval.apibackend.schedule.service.ScheduleRules.TimeDraft;
import com.interval.apibackend.schedule.entity.FrequencyType;
import org.junit.jupiter.api.Test;

/** Matriks aturan jadwal tanpa Spring dan tanpa Docker. */
class ScheduleRulesTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 11);

    private static TimeDraft at(int hour, Integer day) {
        return new TimeDraft(LocalTime.of(hour, 0), day);
    }

    private static ScheduleDraft daily() {
        return new ScheduleDraft(FrequencyType.DAILY, null, new BigDecimal("1"), DoseUnit.TABLET,
                START, null, List.of(at(8, null), at(20, null)));
    }

    private static Map<String, String> validate(ScheduleDraft draft) {
        return ScheduleRules.validate(draft);
    }

    private static ScheduleDraft with(ScheduleDraft d, FrequencyType type, Integer intervalDays, List<TimeDraft> times) {
        return new ScheduleDraft(type, intervalDays, d.doseQuantity(), d.doseUnit(), d.startDate(), d.endDate(), times);
    }

    @Test
    void validDailyWeeklyAndInterval_haveNoErrors() {
        assertThat(validate(daily())).isEmpty();
        assertThat(validate(with(daily(), FrequencyType.WEEKLY, null, List.of(at(8, 1), at(8, 4))))).isEmpty();
        assertThat(validate(with(daily(), FrequencyType.INTERVAL, 3, List.of(at(8, null))))).isEmpty();
    }

    @Test
    void missingRequiredFields_areAllReportedAtOnce() {
        Map<String, String> errors = validate(new ScheduleDraft(null, null, null, null, null, null, null));

        assertThat(errors).containsOnlyKeys("frequencyType", "doseQuantity", "doseUnit", "startDate", "times");
        assertThat(errors.get("times")).isEqualTo("At least one time is required");
    }

    @Test
    void doseQuantity_mustBePositive_withAtMostTwoDecimals_andBounded() {
        ScheduleDraft base = daily();
        java.util.function.Function<String, Map<String, String>> check = value -> validate(new ScheduleDraft(
                base.frequencyType(), null, new BigDecimal(value), base.doseUnit(), START, null, base.times()));

        assertThat(check.apply("0")).containsEntry("doseQuantity", "Dose quantity must be greater than 0");
        assertThat(check.apply("-1")).containsEntry("doseQuantity", "Dose quantity must be greater than 0");
        assertThat(check.apply("0.125")).containsEntry("doseQuantity", "Dose quantity must have at most 2 decimal places");
        assertThat(check.apply("1000000")).containsEntry("doseQuantity", "Dose quantity must be at most 999999.99");
        assertThat(check.apply("0.50")).isEmpty();
        assertThat(check.apply("1.000")).isEmpty();      // trailing zero bukan desimal sungguhan
    }

    @Test
    void endDate_mustNotBeBeforeStartDate_butMayEqualIt() {
        ScheduleDraft base = daily();
        ScheduleDraft before = new ScheduleDraft(base.frequencyType(), null, base.doseQuantity(), base.doseUnit(),
                START, START.minusDays(1), base.times());
        ScheduleDraft same = new ScheduleDraft(base.frequencyType(), null, base.doseQuantity(), base.doseUnit(),
                START, START, base.times());

        assertThat(validate(before)).containsEntry("endDate", "End date must not be earlier than start date");
        assertThat(validate(same)).isEmpty();
    }

    @Test
    void intervalDays_requiredForInterval_forbiddenOtherwise() {
        assertThat(validate(with(daily(), FrequencyType.INTERVAL, null, List.of(at(8, null)))))
                .containsEntry("intervalDays", "Interval days must be at least 1 for INTERVAL schedules");
        assertThat(validate(with(daily(), FrequencyType.INTERVAL, 0, List.of(at(8, null)))))
                .containsKey("intervalDays");
        assertThat(validate(with(daily(), FrequencyType.DAILY, 2, List.of(at(8, null)))))
                .containsEntry("intervalDays", "Interval days is only allowed for INTERVAL schedules");
    }

    @Test
    void dayOfWeek_rulesPerFrequency() {
        assertThat(validate(with(daily(), FrequencyType.WEEKLY, null, List.of(at(8, null)))))
                .containsEntry("times[0].dayOfWeek", "Day of week is required for WEEKLY schedules");
        assertThat(validate(with(daily(), FrequencyType.WEEKLY, null, List.of(at(8, 8)))))
                .containsEntry("times[0].dayOfWeek", "Day of week must be between 1 and 7");
        assertThat(validate(with(daily(), FrequencyType.WEEKLY, null, List.of(at(8, 0)))))
                .containsEntry("times[0].dayOfWeek", "Day of week must be between 1 and 7");
        assertThat(validate(with(daily(), FrequencyType.DAILY, null, List.of(at(8, 1)))))
                .containsEntry("times[0].dayOfWeek", "Day of week must be empty for DAILY and INTERVAL schedules");
        assertThat(validate(with(daily(), FrequencyType.INTERVAL, 2, List.of(at(8, 1)))))
                .containsEntry("times[0].dayOfWeek", "Day of week must be empty for DAILY and INTERVAL schedules");
    }

    @Test
    void times_rules() {
        assertThat(validate(with(daily(), FrequencyType.DAILY, null, List.of())))
                .containsEntry("times", "At least one time is required");

        assertThat(validate(with(daily(), FrequencyType.DAILY, null, List.of(at(8, null), at(8, null)))))
                .containsEntry("times", "Duplicate times are not allowed");

        // Jam sama pada hari berbeda bukan duplikat.
        assertThat(validate(with(daily(), FrequencyType.WEEKLY, null, List.of(at(8, 1), at(8, 2))))).isEmpty();

        List<TimeDraft> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add(new TimeDraft(LocalTime.of(0, 0).plusMinutes(i), null));
        }
        assertThat(validate(with(daily(), FrequencyType.DAILY, null, tooMany)))
                .containsEntry("times", "At most 50 times are allowed");

        List<TimeDraft> withNulls = new ArrayList<>();
        withNulls.add(null);
        withNulls.add(new TimeDraft(null, null));
        assertThat(validate(with(daily(), FrequencyType.DAILY, null, withNulls)))
                .containsEntry("times[0]", "Time entry must not be null")
                .containsEntry("times[1].scheduledTime", "Scheduled time is required");
    }
}
