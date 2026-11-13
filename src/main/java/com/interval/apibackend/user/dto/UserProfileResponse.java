package com.interval.apibackend.user.dto;

import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.interval.apibackend.common.util.TimeUtils;
import com.interval.apibackend.user.entity.User;

public record UserProfileResponse(
        UUID id,
        String fullName,
        String email,
        String timezone,
        // Nama JSON dipaksa "isActive" (komponen record bernama "active" agar tidak ambigu bagi Jackson).
        @JsonProperty("isActive") boolean active,
        String createdAt
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getTimezone(),
                user.isActive(),
                TimeUtils.format(user.getCreatedAt(), ZoneId.of(user.getTimezone())));
    }
}
