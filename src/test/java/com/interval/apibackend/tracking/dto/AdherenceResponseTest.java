package com.interval.apibackend.tracking.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AdherenceResponseTest {

    @Test
    void percentage_matchesTheSpecExample() {
        // 52 taken, 3 skipped, 5 missed -> 52 / 60 = 86,67
        AdherenceResponse response = AdherenceResponse.of(52, 3, 5, 0);

        assertThat(response.totalScheduled()).isEqualTo(60);
        assertThat(response.adherencePercentage()).isEqualByComparingTo("86.67");
    }

    @Test
    void pending_isCountedInTotal_butNotInTheDenominator() {
        AdherenceResponse response = AdherenceResponse.of(2, 1, 0, 1);

        assertThat(response.totalScheduled()).isEqualTo(4);
        assertThat(response.pending()).isEqualTo(1);
        assertThat(response.adherencePercentage()).isEqualByComparingTo("66.67");   // 2/3
    }

    @Test
    void nothingResolvedYet_givesNullNotZeroAndNoError() {
        assertThat(AdherenceResponse.of(0, 0, 0, 4).adherencePercentage()).isNull();
        assertThat(AdherenceResponse.of(0, 0, 0, 0).adherencePercentage()).isNull();
        assertThat(AdherenceResponse.percentage(0, 0)).isNull();
    }

    @Test
    void roundsHalfUpToTwoDecimals_andKeepsScaleTwo() {
        assertThat(AdherenceResponse.percentage(1, 3)).isEqualTo(new BigDecimal("33.33"));
        assertThat(AdherenceResponse.percentage(2, 3)).isEqualTo(new BigDecimal("66.67"));
        assertThat(AdherenceResponse.percentage(1, 8)).isEqualTo(new BigDecimal("12.50"));
        assertThat(AdherenceResponse.percentage(3, 3)).isEqualTo(new BigDecimal("100.00"));
        assertThat(AdherenceResponse.percentage(0, 5)).isEqualTo(new BigDecimal("0.00"));
    }
}
