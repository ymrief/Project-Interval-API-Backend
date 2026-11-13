package com.interval.apibackend.schedule.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.interval.apibackend.schedule.entity.FrequencyType;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** "Tidak dikirim" vs "dikirim null" untuk intervalDays dan endDate. Tanpa Spring, tanpa Docker. */
class UpdateScheduleRequestJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private UpdateScheduleRequest read(String json) throws Exception {
        return mapper.readValue(json, UpdateScheduleRequest.class);
    }

    @Test
    void absentFields_areNotPresent() throws Exception {
        UpdateScheduleRequest request = read("{}");

        assertThat(request.isEndDatePresent()).isFalse();
        assertThat(request.isIntervalDaysPresent()).isFalse();
        assertThat(request.getTimes()).isNull();
        assertThat(request.getActive()).isNull();
    }

    @Test
    void explicitNull_isPresentWithNullValue() throws Exception {
        UpdateScheduleRequest request = read("{\"endDate\":null,\"intervalDays\":null}");

        assertThat(request.isEndDatePresent()).isTrue();
        assertThat(request.getEndDate()).isNull();
        assertThat(request.isIntervalDaysPresent()).isTrue();
        assertThat(request.getIntervalDays()).isNull();
    }

    @Test
    void values_areRead_includingIsActiveAndTimes() throws Exception {
        UpdateScheduleRequest request = read("{\"frequencyType\":\"WEEKLY\",\"endDate\":\"2026-12-31\","
                + "\"isActive\":false,\"times\":[{\"scheduledTime\":\"08:00:00\",\"dayOfWeek\":1}]}");

        assertThat(request.getFrequencyType()).isEqualTo(FrequencyType.WEEKLY);
        assertThat(request.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(request.getActive()).isFalse();
        assertThat(request.getTimes()).hasSize(1);
        assertThat(request.getTimes().get(0).dayOfWeek()).isEqualTo(1);
    }

    @Test
    void clientCannotForgePresenceFlags() throws Exception {
        UpdateScheduleRequest request = read("{\"endDatePresent\":true,\"intervalDaysPresent\":true}");

        assertThat(request.isEndDatePresent()).isFalse();
        assertThat(request.isIntervalDaysPresent()).isFalse();
    }
}
