package com.interval.apibackend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private static final Instant T0 = Instant.parse("2026-11-12T00:00:00Z");

    private JwtService serviceAt(Instant now, String secret) {
        JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(30));
        return new JwtService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void validToken_returnsUserId() {
        UUID userId = UUID.randomUUID();
        String token = serviceAt(T0, SECRET).generateAccessToken(userId);

        assertThat(serviceAt(T0.plus(Duration.ofMinutes(14)), SECRET).parseUserId(token)).contains(userId);
    }

    @Test
    void expiredToken_isRejected() {
        String token = serviceAt(T0, SECRET).generateAccessToken(UUID.randomUUID());

        assertThat(serviceAt(T0.plus(Duration.ofMinutes(16)), SECRET).parseUserId(token)).isEmpty();
    }

    @Test
    void tokenSignedWithOtherSecret_isRejected() {
        String token = serviceAt(T0, "another-secret-another-secret-another-1").generateAccessToken(UUID.randomUUID());

        assertThat(serviceAt(T0, SECRET).parseUserId(token)).isEmpty();
    }

    @Test
    void garbageToken_isRejected() {
        JwtService service = serviceAt(T0, SECRET);

        assertThat(service.parseUserId("not-a-jwt")).isEmpty();
        assertThat(service.parseUserId("")).isEmpty();
    }

    @Test
    void expiresInMatchesConfiguredTtl() {
        assertThat(serviceAt(T0, SECRET).accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void shortSecret_failsFast() {
        assertThatThrownBy(() -> serviceAt(T0, "too-short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
