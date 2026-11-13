package com.interval.apibackend.auth.dto;

import com.interval.apibackend.auth.service.TokenService;
import com.interval.apibackend.user.dto.UserSummary;
import com.interval.apibackend.user.entity.User;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        UserSummary user
) {

    public static LoginResponse of(TokenService.TokenPair pair, User user) {
        return new LoginResponse(pair.accessToken(), TokenResponse.TOKEN_TYPE, pair.expiresIn(),
                pair.refreshToken(), UserSummary.from(user));
    }
}
