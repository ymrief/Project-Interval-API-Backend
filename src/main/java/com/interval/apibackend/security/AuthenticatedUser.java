package com.interval.apibackend.security;

import java.util.UUID;

/** Principal yang disimpan di SecurityContext setelah JWT valid. */
public record AuthenticatedUser(UUID id) {
}
