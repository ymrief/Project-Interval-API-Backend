package com.interval.apibackend.medication.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.interval.apibackend.medication.entity.DosageForm;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Memastikan "tidak dikirim" dan "dikirim null" benar-benar dibedakan. Tanpa Spring, tanpa Docker. */
class UpdateMedicationRequestJsonTest {

    // Boot menonaktifkan FAIL_ON_UNKNOWN_PROPERTIES; tiru di sini.
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private UpdateMedicationRequest read(String json) throws Exception {
        return mapper.readValue(json, UpdateMedicationRequest.class);
    }

    @Test
    void absentFields_areNotPresent() throws Exception {
        UpdateMedicationRequest request = read("{}");

        assertThat(request.isDosagePresent()).isFalse();
        assertThat(request.isInstructionsPresent()).isFalse();
        assertThat(request.isNotesPresent()).isFalse();
        assertThat(request.isImageUrlPresent()).isFalse();
        assertThat(request.getName()).isNull();
        assertThat(request.getActive()).isNull();
    }

    @Test
    void explicitNull_isPresentWithNullValue() throws Exception {
        UpdateMedicationRequest request =
                read("{\"dosage\":null,\"instructions\":null,\"notes\":null,\"imageUrl\":null}");

        assertThat(request.isDosagePresent()).isTrue();
        assertThat(request.isInstructionsPresent()).isTrue();
        assertThat(request.isNotesPresent()).isTrue();
        assertThat(request.isImageUrlPresent()).isTrue();
        assertThat(request.getDosage()).isNull();
        assertThat(request.getNotes()).isNull();
    }

    @Test
    void values_areReadIncludingIsActiveName() throws Exception {
        UpdateMedicationRequest request =
                read("{\"name\":\"X\",\"dosage\":\"5 mg\",\"dosageForm\":\"TABLET\",\"isActive\":false}");

        assertThat(request.getName()).isEqualTo("X");
        assertThat(request.getDosage()).isEqualTo("5 mg");
        assertThat(request.isDosagePresent()).isTrue();
        assertThat(request.getDosageForm()).isEqualTo(DosageForm.TABLET);
        assertThat(request.getActive()).isFalse();
    }

    @Test
    void clientCannotForgePresenceFlags() throws Exception {
        UpdateMedicationRequest request =
                read("{\"dosagePresent\":true,\"notesPresent\":true,\"imageUrlPresent\":true}");

        assertThat(request.isDosagePresent()).isFalse();
        assertThat(request.isNotesPresent()).isFalse();
        assertThat(request.isImageUrlPresent()).isFalse();
    }
}