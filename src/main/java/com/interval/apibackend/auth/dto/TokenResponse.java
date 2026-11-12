package com.interval.apibackend.auth.dto;

import com.interval.apibackend.auth.service.TokenService;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {

    public static final String TOKEN_TYPE = "Bearer";

    public static TokenResponse from(TokenService.TokenPair pair) {
        return new TokenResponse(pair.accessToken(), TOKEN_TYPE, pair.expiresIn(), pair.refreshToken());
    }
}
