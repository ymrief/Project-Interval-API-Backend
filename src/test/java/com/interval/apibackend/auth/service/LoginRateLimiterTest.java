package com.interval.apibackend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.interval.apibackend.common.exception.TooManyRequestsException;
import com.interval.apibackend.support.MutableClock;

/** Tanpa Spring dan tanpa Docker. Batas bawaan: 5 kegagalan per email, 20 per IP, jendela 15 menit. */
class LoginRateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

    private MutableClock clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        limiter = new LoginRateLimiter(new LoginRateLimitProperties(true, 5, 20, Duration.ofMinutes(15)), clock);
    }

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email, ip);
        }
    }

    private long retryAfter(String email, String ip) {
        try {
            limiter.checkAllowed(email, ip);
            return 0;
        } catch (TooManyRequestsException e) {
            return e.getRetryAfterSeconds();
        }
    }

    @Test
    void emailIsBlockedOnlyAfterTheMaximumNumberOfFailures() {
        fail("a@x.com", "1.1.1.1", 4);
        assertThatCode(() -> limiter.checkAllowed("a@x.com", "1.1.1.1")).doesNotThrowAnyException();

        fail("a@x.com", "1.1.1.1", 1);
        assertThatThrownBy(() -> limiter.checkAllowed("a@x.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many login attempts. Please try again later");
    }

    @Test
    void retryAfter_isTheRemainingWindow_roundedUp() {
        fail("a@x.com", "1.1.1.1", 5);
        assertThat(retryAfter("a@x.com", "1.1.1.1")).isEqualTo(900);

        clock.advance(Duration.ofSeconds(100));
        assertThat(retryAfter("a@x.com", "1.1.1.1")).isEqualTo(800);

        clock.set(T0.plusMillis(500));                      // sisa 899,5 detik -> 900
        assertThat(retryAfter("a@x.com", "1.1.1.1")).isEqualTo(900);

        clock.set(T0.plus(Duration.ofMinutes(15)).minusMillis(1));   // sisa 1 ms -> minimal 1 detik
        assertThat(retryAfter("a@x.com", "1.1.1.1")).isEqualTo(1);
    }

    @Test
    void blockEndsWhenTheWindowEnds_andANewWindowStartsFresh() {
        fail("a@x.com", "1.1.1.1", 5);

        clock.set(T0.plus(Duration.ofMinutes(15)));         // tepat di akhir jendela
        assertThatCode(() -> limiter.checkAllowed("a@x.com", "1.1.1.1")).doesNotThrowAnyException();

        fail("a@x.com", "1.1.1.1", 4);                      // jendela baru: baru 4 kegagalan
        assertThatCode(() -> limiter.checkAllowed("a@x.com", "1.1.1.1")).doesNotThrowAnyException();
        fail("a@x.com", "1.1.1.1", 1);
        assertThat(retryAfter("a@x.com", "1.1.1.1")).isEqualTo(900);
    }

    @Test
    void ipIsBlockedAcrossDifferentEmails_otherIpsAreNot() {
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("user" + i + "@x.com", "1.1.1.1");    // tiap email hanya 1 kegagalan
        }

        assertThat(retryAfter("brand-new@x.com", "1.1.1.1")).isPositive();
        assertThat(retryAfter("brand-new@x.com", "2.2.2.2")).isZero();
    }

    @Test
    void blockedEmailStaysBlockedWhenTheAttackerRotatesIps_butOtherEmailsAreUnaffected() {
        fail("victim@x.com", "1.1.1.1", 5);

        assertThat(retryAfter("victim@x.com", "9.9.9.9")).isPositive();     // ganti IP tidak membantu
        assertThat(retryAfter("other@x.com", "1.1.1.1")).isZero();           // email lain tidak ikut terblokir
    }

    @Test
    void success_resetsTheEmailCounter_butNotTheIpCounter() {
        fail("a@x.com", "1.1.1.1", 4);
        limiter.recordSuccess("a@x.com");

        fail("a@x.com", "1.1.1.1", 4);                       // total 8 kegagalan IP, tetapi email baru 4
        assertThat(retryAfter("a@x.com", "1.1.1.1")).isZero();

        for (int i = 0; i < 12; i++) {                       // IP mencapai 20 kegagalan
            limiter.recordFailure("user" + i + "@x.com", "1.1.1.1");
        }
        assertThat(retryAfter("a@x.com", "1.1.1.1")).isPositive();           // hitungan IP tidak ikut dikosongkan
    }

    @Test
    void whenDisabled_neverBlocksAndTracksNothing() {
        LoginRateLimiter disabled = new LoginRateLimiter(
                new LoginRateLimitProperties(false, 5, 20, Duration.ofMinutes(15)), clock);

        for (int i = 0; i < 100; i++) {
            disabled.recordFailure("a@x.com", "1.1.1.1");
        }

        assertThatCode(() -> disabled.checkAllowed("a@x.com", "1.1.1.1")).doesNotThrowAnyException();
        assertThat(disabled.trackedKeys()).isZero();
    }

    @Test
    void purgeExpired_removesOnlyFinishedWindows() {
        fail("a@x.com", "1.1.1.1", 1);
        assertThat(limiter.trackedKeys()).isEqualTo(2);      // satu kunci email + satu kunci IP

        clock.advance(Duration.ofMinutes(10));
        fail("b@x.com", "2.2.2.2", 1);
        assertThat(limiter.purgeExpired()).isZero();         // belum ada yang berakhir

        clock.advance(Duration.ofMinutes(6));                // a/1.1.1.1 berakhir, b/2.2.2.2 belum
        assertThat(limiter.purgeExpired()).isEqualTo(2);
        assertThat(limiter.trackedKeys()).isEqualTo(2);

        limiter.clear();
        assertThat(limiter.trackedKeys()).isZero();
    }
}
