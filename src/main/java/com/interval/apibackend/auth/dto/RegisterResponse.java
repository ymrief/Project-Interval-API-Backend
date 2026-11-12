package com.interval.apibackend.auth.dto;

import java.util.UUID;

public record RegisterResponse(UUID id, String fullName, String email, String timezone) {
}
