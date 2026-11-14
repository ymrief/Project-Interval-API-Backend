package com.interval.apibackend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.interval.apibackend.tracking.service.OccurrenceProperties;

/**
 * Mengaktifkan @Scheduled. Komponen yang berisi job (OccurrenceJobs) dikendalikan lewat
 * interval.scheduling.enabled (default true), jadi mudah dimatikan di test.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OccurrenceProperties.class)
public class SchedulingConfig {
}
