package com.interval.apibackend.auth.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * interval.security.login-rate-limit.enabled                : default true
 * interval.security.login-rate-limit.max-failures-per-email : default 5
 * interval.security.login-rate-limit.max-failures-per-ip    : default 20
 * interval.security.login-rate-limit.window                 : default 15m
 */
@ConfigurationProperties(prefix = "interval.security.login-rate-limit")
public record LoginRateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") int maxFailuresPerEmail,
        @DefaultValue("20") int maxFailuresPerIp,
        @DefaultValue("15m") Duration window
) {
}
