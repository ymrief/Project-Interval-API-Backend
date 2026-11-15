package com.interval.apibackend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.interval.apibackend.auth.service.LoginRateLimitProperties;

@Configuration
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class RateLimitConfig {
}
