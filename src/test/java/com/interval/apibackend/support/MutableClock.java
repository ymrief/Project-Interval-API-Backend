package com.interval.apibackend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Jam yang bisa dimajukan dari test, agar logika berbasis waktu bisa diuji tanpa Thread.sleep. */
public class MutableClock extends Clock {

    private volatile Instant instant;

    public MutableClock(Instant initial) {
        this.instant = initial;
    }

    public void set(Instant newInstant) {
        this.instant = newInstant;
    }

    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}