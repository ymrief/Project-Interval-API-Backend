package com.interval.apibackend.notification.service;

import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.common.response.PageResponse;
import com.interval.apibackend.notification.dto.NotificationHistoryItem;
import com.interval.apibackend.notification.entity.DeliveryStatus;
import com.interval.apibackend.notification.entity.NotificationLog;
import com.interval.apibackend.notification.repository.NotificationLogRepository;
import com.interval.apibackend.user.service.UserTimezoneService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    private final NotificationLogRepository logRepository;
    private final UserTimezoneService timezoneService;

    /** Dibatasi per pengguna lewat kolom user_id (tanpa join berlapis). Terbaru dulu. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationHistoryItem> list(UUID userId, DeliveryStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Page<NotificationLog> result = status == null
                ? logRepository.findByUserId(userId, pageable)
                : logRepository.findByUserIdAndDeliveryStatus(userId, status, pageable);

        ZoneId zone = timezoneService.zoneOf(userId);
        return PageResponse.from(result, log -> NotificationHistoryItem.from(log, zone));
    }
}
