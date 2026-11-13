package com.interval.apibackend.user.controller;

import com.interval.apibackend.auth.dto.TokenResponse;
import com.interval.apibackend.common.response.ApiResponse;
import com.interval.apibackend.security.CurrentUser;
import com.interval.apibackend.user.dto.ChangePasswordRequest;
import com.interval.apibackend.user.dto.UpdateProfileRequest;
import com.interval.apibackend.user.dto.UserProfileResponse;
import com.interval.apibackend.user.dto.UserSummary;
import com.interval.apibackend.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Semua endpoint di sini terlindungi (anyRequest().authenticated()); userId selalu dari token. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<UserProfileResponse> getCurrentUser() {
        return ApiResponse.success("User profile retrieved successfully",
                userService.getProfile(CurrentUser.id()));
    }

    @PatchMapping
    public ApiResponse<UserSummary> updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success("Profile updated successfully",
                userService.updateProfile(CurrentUser.id(), request));
    }

    @PatchMapping("/password")
    public ApiResponse<TokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.success("Password changed successfully",
                userService.changePassword(CurrentUser.id(), request));
    }
}
