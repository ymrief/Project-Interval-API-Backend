package com.interval.apibackend.notification.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interval.apibackend.common.response.ApiResponse;
import com.interval.apibackend.common.response.PageResponse;
import com.interval.apibackend.notification.dto.NotificationHistoryItem;
import com.interval.apibackend.notification.dto.PreferencesResponse;
import com.interval.apibackend.notification.dto.UpdatePreferencesRequest;
import com.interval.apibackend.notification.entity.DeliveryStatus;
import com.interval.apibackend.notification.service.NotificationHistoryService;
import com.interval.apibackend.notification.service.NotificationPreferenceService;
import com.interval.apibackend.security.CurrentUser;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationPreferenceService preferenceService;
    private final NotificationHistoryService historyService;

    @GetMapping("/notification-preferences")
    public ApiResponse<PreferencesResponse> getPreferences() {
        return ApiResponse.success("Notification preferences retrieved successfully",
                preferenceService.get(CurrentUser.id()));
    }

    @PatchMapping("/notification-preferences")
    public ApiResponse<PreferencesResponse> updatePreferences(@RequestBody UpdatePreferencesRequest request) {
        return ApiResponse.success("Notification preferences updated successfully",
                preferenceService.update(CurrentUser.id(), request));
    }

    @GetMapping("/notifications/history")
    public ApiResponse<PageResponse<NotificationHistoryItem>> history(
            @RequestParam(required = false) DeliveryStatus deliveryStatus,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must not be negative") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be between 1 and 100")
            @Max(value = 100, message = "Size must be between 1 and 100") int size) {
        return ApiResponse.success("Notification history retrieved successfully",
                historyService.list(CurrentUser.id(), deliveryStatus, page, size));
    }
}
