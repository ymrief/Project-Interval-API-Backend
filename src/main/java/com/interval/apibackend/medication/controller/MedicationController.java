package com.interval.apibackend.medication.controller;

import java.util.UUID;

import com.interval.apibackend.common.response.ApiResponse;
import com.interval.apibackend.common.response.PageResponse;
import com.interval.apibackend.medication.dto.CreateMedicationRequest;
import com.interval.apibackend.medication.dto.MedicationResponse;
import com.interval.apibackend.medication.dto.UpdateMedicationRequest;
import com.interval.apibackend.medication.service.MedicationService;
import com.interval.apibackend.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    @GetMapping
    public ApiResponse<PageResponse<MedicationResponse>> list(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must not be negative") int page,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Size must be between 1 and 100")
            @Max(value = 100, message = "Size must be between 1 and 100") int size,

            @RequestParam(required = false) Boolean active) {
        return ApiResponse.success("Medication list retrieved successfully",
                medicationService.list(CurrentUser.id(), page, size, active));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MedicationResponse>> create(
            @Valid @RequestBody CreateMedicationRequest request) {
        MedicationResponse data = medicationService.create(CurrentUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Medication created successfully", data));
    }

    @GetMapping("/{medicationId}")
    public ApiResponse<MedicationResponse> get(@PathVariable UUID medicationId) {
        return ApiResponse.success("Medication retrieved successfully",
                medicationService.get(CurrentUser.id(), medicationId));
    }

    @PatchMapping("/{medicationId}")
    public ApiResponse<MedicationResponse> update(@PathVariable UUID medicationId,
                                                  @Valid @RequestBody UpdateMedicationRequest request) {
        return ApiResponse.success("Medication updated successfully",
                medicationService.update(CurrentUser.id(), medicationId, request));
    }

    @DeleteMapping("/{medicationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID medicationId) {
        medicationService.delete(CurrentUser.id(), medicationId);
        return ResponseEntity.noContent().build();
    }
}
