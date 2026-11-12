package com.interval.apibackend.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * interval.jwt.secret           : minimal 32 byte, isi dari environment variable JWT_SECRET
 * interval.jwt.access-token-ttl : default 15m  (expiresIn: 900 pada spec)
 * interval.jwt.refresh-token-ttl: default 30d
 */
@ConfigurationProperties(prefix = "interval.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl
) {
}
