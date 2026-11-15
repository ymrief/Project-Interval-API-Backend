package com.interval.apibackend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Mengganti PushSender aplikasi dengan FakePushSender. Pakai dengan @Import(TestPushConfig.class). */
@TestConfiguration(proxyBeanMethods = false)
public class TestPushConfig {

    @Bean
    @Primary
    FakePushSender fakePushSender() {
        return new FakePushSender();
    }
}
