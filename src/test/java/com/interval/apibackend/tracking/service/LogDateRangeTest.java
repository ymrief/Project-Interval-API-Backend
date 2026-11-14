package com.interval.apibackend.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.interval.apibackend.common.exception.BusinessRuleException;

class LogDateRangeTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @Test
    void noParameters_meansLast30DaysEndingToday() {
        LogDateRange range = LogDateRange.resolve(null, null, JAKARTA, TODAY);

        assertThat(range.to()).isEqualTo(TODAY);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 14));   // 30 hari inklusif
    }

    @Test
    void onlyTo_defaultsFromTo30DaysBeforeIt() {
        LogDateRange range = LogDateRange.resolve(null, LocalDate.of(2026, 11, 20), JAKARTA, TODAY);

        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    void onlyFrom_endsToday() {
        LogDateRange range = LogDateRange.resolve(LocalDate.of(2026, 11, 1), null, JAKARTA, TODAY);

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(range.to()).isEqualTo(TODAY);
    }

    @Test
    void boundaries_areLocalMidnights_endExclusive() {
        LogDateRange range = LogDateRange.resolve(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 13), JAKARTA, TODAY);

        assertThat(range.start()).isEqualTo(Instant.parse("2026-11-12T17:00:00Z"));          // 13 Nov 00:00 WIB
        assertThat(range.endExclusive()).isEqualTo(Instant.parse("2026-11-13T17:00:00Z"));   // 14 Nov 00:00 WIB
    }

    @Test
    void singleDay_isValid() {
        assertThat(LogDateRange.resolve(TODAY, TODAY, JAKARTA, TODAY).from()).isEqualTo(TODAY);
    }

    @Test
    void toBeforeFrom_isRejected() {
        assertThatThrownBy(() -> LogDateRange.resolve(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 13), JAKARTA, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Invalid date range")
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrors())
                        .containsEntry("to", "End date must not be earlier than start date"));
    }

    @Test
    void maximumIs366DaysInclusive() {
        LocalDate to = LocalDate.of(2026, 11, 12);

        assertThat(LogDateRange.resolve(to.minusDays(365), to, JAKARTA, TODAY)).isNotNull();   // tepat 366 hari
        assertThatThrownBy(() -> LogDateRange.resolve(to.minusDays(366), to, JAKARTA, TODAY)) // 367 hari
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrors())
                        .containsEntry("to", "Date range must not exceed 366 days"));
    }
}
