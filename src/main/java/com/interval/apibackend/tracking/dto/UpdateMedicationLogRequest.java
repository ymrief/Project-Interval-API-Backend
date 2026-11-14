package com.interval.apibackend.tracking.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.interval.apibackend.tracking.entity.LogStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * status wajib. notes nullable: tidak dikirim = tidak diubah, dikirim null/kosong = dikosongkan
 * (pola presence yang sama dengan UpdateMedicationRequest). Client tidak punya cara mengubah
 * scheduleId, scheduleTimeId, scheduledAt, atau snapshot dosis: field itu memang tidak ada di sini.
 */
public class UpdateMedicationLogRequest {

    @NotNull(message = "Status is required")
    private LogStatus status;

    @Size(max = 5000, message = "Notes must be at most 5000 characters")
    private String notes;
    @JsonIgnore
    private boolean notesPresent;

    public void setStatus(LogStatus status) {
        this.status = status;
    }

    @JsonSetter("notes")
    public void setNotes(String notes) {
        this.notes = notes;
        this.notesPresent = true;
    }

    public LogStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isNotesPresent() {
        return notesPresent;
    }
}
