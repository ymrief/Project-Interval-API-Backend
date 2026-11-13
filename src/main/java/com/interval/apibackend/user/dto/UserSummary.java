package com.interval.apibackend.user.dto;

import com.interval.apibackend.user.entity.User;

import java.util.UUID;

public record UserSummary(UUID id, String fullName, String email, String timezone) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getTimezone());
    }
}
