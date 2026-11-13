package com.interval.apibackend.medication.dto;

import com.interval.apibackend.medication.entity.DosageForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMedicationRequest(
        @NotBlank(message = "Medication name is required")
        @Size(max = 150, message = "Medication name must be at most 150 characters")
        String name,

        @Size(max = 100, message = "Dosage must be at most 100 characters")
        String dosage,

        // null -> OTHER. Nilai di luar enum ditolak 422 oleh GlobalExceptionHandler.
        DosageForm dosageForm,

        @Size(max = 5000, message = "Instructions must be at most 5000 characters")
        String instructions,

        @Size(max = 5000, message = "Notes must be at most 5000 characters")
        String notes,

        // Hanya http/https (mencegah skema seperti javascript: tersimpan lalu dirender client).
        @Size(max = 500, message = "Image URL must be at most 500 characters")
        @Pattern(regexp = "(?i)^(https?://\\S+)?$", message = "Image URL must start with http:// or https://")
        String imageUrl
) {
}
