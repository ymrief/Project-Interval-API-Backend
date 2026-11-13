package com.interval.apibackend.medication.dto;

import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.interval.apibackend.common.util.TimeUtils;
import com.interval.apibackend.medication.entity.DosageForm;
import com.interval.apibackend.medication.entity.Medication;

/** Bentuk yang sama untuk list, detail, create, dan update. */
public record MedicationResponse(
        UUID id,
        String name,
        String dosage,
        DosageForm dosageForm,
        String instructions,
        String notes,
        String imageUrl,
        @JsonProperty("isActive") boolean active,
        String createdAt,
        String updatedAt
) {

    public static MedicationResponse from(Medication medication, ZoneId zone) {
        return new MedicationResponse(
                medication.getId(),
                medication.getName(),
                medication.getDosage(),
                medication.getDosageForm(),
                medication.getInstructions(),
                medication.getNotes(),
                medication.getImageUrl(),
                medication.isActive(),
                TimeUtils.format(medication.getCreatedAt(), zone),
                TimeUtils.format(medication.getUpdatedAt(), zone));
    }
}
