package com.interval.apibackend.security;

import java.util.UUID;

import com.interval.apibackend.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Satu-satunya cara mengambil identitas pengguna di service/controller.
 * userId SELALU berasal dari token, tidak pernah dari body/path/query.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        throw new UnauthorizedException("Unauthorized");
    }
}
