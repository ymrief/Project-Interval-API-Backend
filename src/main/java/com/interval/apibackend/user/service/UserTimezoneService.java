package com.interval.apibackend.user.service;

import java.time.ZoneId;
import java.util.UUID;

import com.interval.apibackend.common.exception.UnauthorizedException;
import com.interval.apibackend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Timezone pengguna untuk memformat waktu di response (TimeUtils.format).
 * Dipakai modul Medication, Schedule, Tracking, dan Dashboard.
 */
@Service
@RequiredArgsConstructor
public class UserTimezoneService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID userId) {
        return userRepository.findTimezoneById(userId)
                .map(ZoneId::of)
                .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }
}
