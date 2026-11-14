package com.interval.apibackend.tracking.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interval.apibackend.common.response.ApiResponse;
import com.interval.apibackend.common.response.PageResponse;
import com.interval.apibackend.security.CurrentUser;
import com.interval.apibackend.tracking.dto.AdherenceResponse;
import com.interval.apibackend.tracking.dto.MedicationLogResponse;
import com.interval.apibackend.tracking.dto.UpdateMedicationLogRequest;
import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.service.AdherenceService;
import com.interval.apibackend.tracking.service.MedicationLogService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * Tipe parameter yang salah (tanggal bukan YYYY-MM-DD, status di luar enum, UUID rusak) menghasilkan 400;
 * rentang tanggal terbalik atau terlalu panjang menghasilkan 422 (dilempar service).
 */
@RestController
@RequestMapping("/api/medication-logs")
@RequiredArgsConstructor
public class MedicationLogController {

    private final MedicationLogService logService;
    private final AdherenceService adherenceService;

    @GetMapping
    public ApiResponse<PageResponse<MedicationLogResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) LogStatus status,
            @RequestParam(required = false) UUID medicationId,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must not be negative") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be between 1 and 100")
            @Max(value = 100, message = "Size must be between 1 and 100") int size) {
        return ApiResponse.success("Medication logs retrieved successfully",
                logService.list(CurrentUser.id(), from, to, status, medicationId, page, size));
    }

    // Jalur literal "/adherence" selalu diprioritaskan Spring atas "/{logId}".
    @GetMapping("/adherence")
    public ApiResponse<AdherenceResponse> adherence(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID medicationId) {
        return ApiResponse.success("Medication adherence retrieved successfully",
                adherenceService.forDates(CurrentUser.id(), from, to, medicationId));
    }

    @GetMapping("/{logId}")
    public ApiResponse<MedicationLogResponse> get(@PathVariable UUID logId) {
        return ApiResponse.success("Medication log retrieved successfully",
                logService.get(CurrentUser.id(), logId));
    }

    @PatchMapping("/{logId}")
    public ApiResponse<MedicationLogResponse> update(@PathVariable UUID logId,
                                                     @Valid @RequestBody UpdateMedicationLogRequest request) {
        return ApiResponse.success("Medication log updated successfully",
                logService.updateStatus(CurrentUser.id(), logId, request));
    }
}
