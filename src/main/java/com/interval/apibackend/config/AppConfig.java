package com.interval.apibackend.config;

import java.time.Clock;

import com.interval.apibackend.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AppConfig {

    /** Clock tunggal agar logika berbasis waktu (token, generator occurrence) mudah dites. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
