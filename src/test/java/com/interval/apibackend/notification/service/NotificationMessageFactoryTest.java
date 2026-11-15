package com.interval.apibackend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.interval.apibackend.notification.entity.NotificationType;
import com.interval.apibackend.notification.push.PushMessage;
import com.interval.apibackend.schedule.entity.DoseUnit;

class NotificationMessageFactoryTest {

    private static final UUID LOG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant SCHEDULED = Instant.parse("2026-11-12T10:30:00Z");

    private static DueNotification due(String dose, DoseUnit unit, boolean sound, int minutes) {
        return new DueNotification(LOG_ID, UUID.randomUUID(), "Paracetamol", new BigDecimal(dose), unit,
                SCHEDULED, sound, minutes);
    }

    private static PushMessage create(NotificationType type, DueNotification due) {
        return NotificationMessageFactory.create(type, due, "token-123");
    }

    @Test
    void reminder_hasTitleAndBodyWithMedicationAndDose() {
        PushMessage message = create(NotificationType.MEDICATION_REMINDER, due("1.00", DoseUnit.TABLET, true, 0));

        assertThat(message.token()).isEqualTo("token-123");
        assertThat(message.title()).isEqualTo("Waktunya minum obat");
        assertThat(message.body()).isEqualTo("Paracetamol, 1 tablet");
    }

    @Test
    void preReminder_mentionsHowManyMinutesAreLeft() {
        PushMessage message = create(NotificationType.PRE_REMINDER, due("1.00", DoseUnit.TABLET, true, 10));

        assertThat(message.title()).isEqualTo("Sebentar lagi waktunya minum obat");
        assertThat(message.body()).isEqualTo("Paracetamol, 1 tablet dalam 10 menit");
    }

    @Test
    void missedAlert_hasItsOwnText() {
        PushMessage message = create(NotificationType.MISSED_ALERT, due("1.00", DoseUnit.TABLET, true, 0));

        assertThat(message.title()).isEqualTo("Obat terlewat");
        assertThat(message.body()).isEqualTo("Paracetamol, 1 tablet belum diminum");
    }

    @Test
    void dose_isFormattedWithoutTrailingZeros_andWithTheRightUnitLabel() {
        assertThat(create(NotificationType.MEDICATION_REMINDER, due("0.50", DoseUnit.ML, true, 0)).body())
                .isEqualTo("Paracetamol, 0.5 ml");
        assertThat(create(NotificationType.MEDICATION_REMINDER, due("2.00", DoseUnit.CAPSULE, true, 0)).body())
                .isEqualTo("Paracetamol, 2 kapsul");
        assertThat(create(NotificationType.MEDICATION_REMINDER, due("5.00", DoseUnit.DROP, true, 0)).body())
                .isEqualTo("Paracetamol, 5 tetes");
        assertThat(create(NotificationType.MEDICATION_REMINDER, due("1.25", DoseUnit.OTHER, true, 0)).body())
                .isEqualTo("Paracetamol, 1.25 dosis");
        // 10 tidak boleh menjadi "1E+1"
        assertThat(create(NotificationType.MEDICATION_REMINDER, due("10.00", DoseUnit.ML, true, 0)).body())
                .isEqualTo("Paracetamol, 10 ml");
    }

    @Test
    void data_carriesTypeLogIdScheduledTimeAndSoundFlag_asStrings() {
        PushMessage message = create(NotificationType.PRE_REMINDER, due("1.00", DoseUnit.TABLET, false, 10));

        assertThat(message.sound()).isFalse();
        assertThat(message.data()).containsOnly(
                java.util.Map.entry("type", "PRE_REMINDER"),
                java.util.Map.entry("medicationLogId", LOG_ID.toString()),
                java.util.Map.entry("scheduledAt", "2026-11-12T10:30:00Z"),
                java.util.Map.entry("sound", "false"));
    }
}
