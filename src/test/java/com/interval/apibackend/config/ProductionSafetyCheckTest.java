package com.interval.apibackend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionSafetyCheckTest {

    private static final String STRONG_SECRET = "k8Zp3vQ9xLw2Rt7YcNd5HgJm1BsVa6FuEoXi4TqWz0Ay8KbCe";

    @Test
    void strongSecretAndFcmEnabled_isAccepted() {
        assertThatCode(() -> ProductionSafetyCheck.validate(STRONG_SECRET, true)).doesNotThrowAnyException();
    }

    @Test
    void missingOrShortSecret_isRejected() {
        assertThatThrownBy(() -> ProductionSafetyCheck.validate(null, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> ProductionSafetyCheck.validate("terlalu-pendek", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32 bytes");
    }

    @Test
    void developmentPlaceholderSecret_isRejected() {
        assertThatThrownBy(() -> ProductionSafetyCheck.validate(
                "dev-only-secret-change-me-dev-only-secret-0123456789", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("development placeholder");
        assertThatThrownBy(() -> ProductionSafetyCheck.validate(
                "please-change-me-please-change-me-please-change-me", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("development placeholder");
    }

    @Test
    void disabledFcm_isRejected() {
        assertThatThrownBy(() -> ProductionSafetyCheck.validate(STRONG_SECRET, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fcm.enabled must be true");
    }

    @Test
    void allProblemsAreReportedTogether() {
        assertThatThrownBy(() -> ProductionSafetyCheck.validate("pendek", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes")
                .hasMessageContaining("fcm.enabled must be true");
    }
}
