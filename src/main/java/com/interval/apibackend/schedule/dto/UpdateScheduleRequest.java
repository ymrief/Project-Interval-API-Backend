package com.interval.apibackend.schedule.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.interval.apibackend.schedule.entity.DoseUnit;
import com.interval.apibackend.schedule.entity.FrequencyType;

/**
 * PATCH parsial. intervalDays dan endDate nullable, jadi dibedakan:
 *   tidak dikirim = tidak diubah, dikirim null = dikosongkan (pola yang sama dengan UpdateMedicationRequest).
 * Field lain: null disamakan dengan tidak dikirim. startDate tidak bisa diubah (nilai berbeda ditolak).
 */
public class UpdateScheduleRequest {

    private FrequencyType frequencyType;

    private Integer intervalDays;
    @JsonIgnore
    private boolean intervalDaysPresent;

    private BigDecimal doseQuantity;
    private DoseUnit doseUnit;
    private LocalDate startDate;

    private LocalDate endDate;
    @JsonIgnore
    private boolean endDatePresent;

    @JsonProperty("isActive")
    private Boolean active;

    private List<ScheduleTimeRequest> times;

    // ---------- setter untuk Jackson ----------

    public void setFrequencyType(FrequencyType frequencyType) {
        this.frequencyType = frequencyType;
    }

    @JsonSetter("intervalDays")
    public void setIntervalDays(Integer intervalDays) {
        this.intervalDays = intervalDays;
        this.intervalDaysPresent = true;
    }

    public void setDoseQuantity(BigDecimal doseQuantity) {
        this.doseQuantity = doseQuantity;
    }

    public void setDoseUnit(DoseUnit doseUnit) {
        this.doseUnit = doseUnit;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    @JsonSetter("endDate")
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDatePresent = true;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setTimes(List<ScheduleTimeRequest> times) {
        this.times = times;
    }

    // ---------- getter ----------

    public FrequencyType getFrequencyType() {
        return frequencyType;
    }

    public Integer getIntervalDays() {
        return intervalDays;
    }

    public boolean isIntervalDaysPresent() {
        return intervalDaysPresent;
    }

    public BigDecimal getDoseQuantity() {
        return doseQuantity;
    }

    public DoseUnit getDoseUnit() {
        return doseUnit;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isEndDatePresent() {
        return endDatePresent;
    }

    public Boolean getActive() {
        return active;
    }

    public List<ScheduleTimeRequest> getTimes() {
        return times;
    }
}
