package com.interval.apibackend.schedule.controller;

import java.util.List;
import java.util.UUID;

import com.interval.apibackend.common.response.ApiResponse;
import com.interval.apibackend.schedule.dto.CreateScheduleRequest;
import com.interval.apibackend.schedule.dto.ScheduleResponse;
import com.interval.apibackend.schedule.dto.UpdateScheduleRequest;
import com.interval.apibackend.schedule.service.ScheduleService;
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

import lombok.RequiredArgsConstructor;

/**
 * Tidak memakai @Valid: validasi jadwal ada di ScheduleRules (pesan "Invalid schedule configuration").
 * Jalur berbeda untuk create karena jadwal selalu dibuat di bawah sebuah obat.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/schedules")
    public ApiResponse<List<ScheduleResponse>> list(@RequestParam(required = false) UUID medicationId,
                                                    @RequestParam(required = false) Boolean active) {
        return ApiResponse.success("Schedule list retrieved successfully",
                scheduleService.list(CurrentUser.id(), medicationId, active));
    }

    @PostMapping("/medications/{medicationId}/schedules")
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(@PathVariable UUID medicationId,
                                                                @RequestBody CreateScheduleRequest request) {
        ScheduleResponse data = scheduleService.create(CurrentUser.id(), medicationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Schedule created successfully", data));
    }

    @GetMapping("/schedules/{scheduleId}")
    public ApiResponse<ScheduleResponse> get(@PathVariable UUID scheduleId) {
        return ApiResponse.success("Schedule retrieved successfully",
                scheduleService.get(CurrentUser.id(), scheduleId));
    }

    @PatchMapping("/schedules/{scheduleId}")
    public ApiResponse<ScheduleResponse> update(@PathVariable UUID scheduleId,
                                                @RequestBody UpdateScheduleRequest request) {
        return ApiResponse.success("Schedule updated successfully",
                scheduleService.update(CurrentUser.id(), scheduleId, request));
    }

    @DeleteMapping("/schedules/{scheduleId}")
    public ResponseEntity<Void> delete(@PathVariable UUID scheduleId) {
        scheduleService.delete(CurrentUser.id(), scheduleId);
        return ResponseEntity.noContent().build();
    }
}
