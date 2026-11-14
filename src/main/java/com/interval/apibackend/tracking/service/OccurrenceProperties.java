package com.interval.apibackend.tracking.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * interval.occurrence.horizon    : seberapa jauh ke depan occurrence dibuat (default 48h)
 * interval.occurrence.missed-after: toleransi sebelum PENDING dianggap MISSED (default 60m)
 */
@ConfigurationProperties(prefix = "interval.occurrence")
public record OccurrenceProperties(
        @DefaultValue("48h") Duration horizon,
        @DefaultValue("60m") Duration missedAfter
) {
}
