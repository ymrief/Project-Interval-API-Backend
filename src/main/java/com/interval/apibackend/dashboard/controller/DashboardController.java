package com.interval.apibackend.dashboard.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interval.apibackend.common.response.ApiResponse;
import com.interval.apibackend.dashboard.dto.DashboardSummaryResponse;
import com.interval.apibackend.dashboard.dto.TodayScheduleItem;
import com.interval.apibackend.dashboard.service.DashboardService;
import com.interval.apibackend.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.success("Dashboard summary retrieved successfully",
                dashboardService.summary(CurrentUser.id()));
    }

    @GetMapping("/today")
    public ApiResponse<List<TodayScheduleItem>> today() {
        return ApiResponse.success("Today's schedule retrieved successfully",
                dashboardService.today(CurrentUser.id()));
    }
}
