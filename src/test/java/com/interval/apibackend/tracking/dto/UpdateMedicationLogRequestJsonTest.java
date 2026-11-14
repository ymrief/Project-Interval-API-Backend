package com.interval.apibackend.tracking.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interval.apibackend.tracking.entity.LogStatus;

class UpdateMedicationLogRequestJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private UpdateMedicationLogRequest read(String json) throws Exception {
        return mapper.readValue(json, UpdateMedicationLogRequest.class);
    }

    @Test
    void absentNotes_isNotPresent() throws Exception {
        UpdateMedicationLogRequest request = read("{\"status\":\"TAKEN\"}");

        assertThat(request.getStatus()).isEqualTo(LogStatus.TAKEN);
        assertThat(request.isNotesPresent()).isFalse();
    }

    @Test
    void explicitNullNotes_isPresent() throws Exception {
        UpdateMedicationLogRequest request = read("{\"status\":\"SKIPPED\",\"notes\":null}");

        assertThat(request.isNotesPresent()).isTrue();
        assertThat(request.getNotes()).isNull();
    }

    @Test
    void clientCannotForgePresence_orSetImmutableFields() throws Exception {
        UpdateMedicationLogRequest request = read(
                "{\"status\":\"TAKEN\",\"notesPresent\":true,\"scheduledAt\":\"2020-01-01T00:00:00Z\"}");

        assertThat(request.isNotesPresent()).isFalse();   // field lain diabaikan, tidak ada tempat untuk scheduledAt
    }
}
