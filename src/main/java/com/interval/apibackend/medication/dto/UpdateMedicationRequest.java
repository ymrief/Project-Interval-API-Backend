package com.interval.apibackend.medication.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.interval.apibackend.medication.entity.DosageForm;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH parsial. Dibuat sebagai class (bukan record) karena field nullable harus bisa membedakan:
 *   - tidak dikirim  -> tidak diubah
 *   - dikirim null   -> dikosongkan
 * Jackson memanggil setter HANYA jika propertinya ada di JSON (walau nilainya null),
 * sehingga setter menandai "present". Pola yang sama dipakai lagi di Schedule (endDate) dan Tracking (notes).
 *
 * Field wajib (name, dosageForm, isActive): null disamakan dengan tidak dikirim.
 */
public class UpdateMedicationRequest {

    @Pattern(regexp = "(?s).*\\S.*", message = "Medication name must not be blank")
    @Size(max = 150, message = "Medication name must be at most 150 characters")
    private String name;

    @Size(max = 100, message = "Dosage must be at most 100 characters")
    private String dosage;
    @JsonIgnore
    private boolean dosagePresent;

    private DosageForm dosageForm;

    @Size(max = 5000, message = "Instructions must be at most 5000 characters")
    private String instructions;
    @JsonIgnore
    private boolean instructionsPresent;

    @Size(max = 5000, message = "Notes must be at most 5000 characters")
    private String notes;
    @JsonIgnore
    private boolean notesPresent;

    @Size(max = 500, message = "Image URL must be at most 500 characters")
    @Pattern(regexp = "(?i)^(https?://\\S+)?$", message = "Image URL must start with http:// or https://")
    private String imageUrl;
    @JsonIgnore
    private boolean imageUrlPresent;

    @JsonProperty("isActive")
    private Boolean active;

    // ---------- setter untuk Jackson ----------

    public void setName(String name) {
        this.name = name;
    }

    @JsonSetter("dosage")
    public void setDosage(String dosage) {
        this.dosage = dosage;
        this.dosagePresent = true;
    }

    public void setDosageForm(DosageForm dosageForm) {
        this.dosageForm = dosageForm;
    }

    @JsonSetter("instructions")
    public void setInstructions(String instructions) {
        this.instructions = instructions;
        this.instructionsPresent = true;
    }

    @JsonSetter("notes")
    public void setNotes(String notes) {
        this.notes = notes;
        this.notesPresent = true;
    }

    @JsonSetter("imageUrl")
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        this.imageUrlPresent = true;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    // ---------- getter ----------

    public String getName() {
        return name;
    }

    public String getDosage() {
        return dosage;
    }

    public boolean isDosagePresent() {
        return dosagePresent;
    }

    public DosageForm getDosageForm() {
        return dosageForm;
    }

    public String getInstructions() {
        return instructions;
    }

    public boolean isInstructionsPresent() {
        return instructionsPresent;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isNotesPresent() {
        return notesPresent;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isImageUrlPresent() {
        return imageUrlPresent;
    }

    public Boolean getActive() {
        return active;
    }
}
