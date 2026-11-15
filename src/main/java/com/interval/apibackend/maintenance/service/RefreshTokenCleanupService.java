package com.interval.apibackend.maintenance.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.interval.apibackend.maintenance.repository.RefreshTokenCleanupRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Membuang refresh token yang sudah kedaluwarsa lebih dari masa simpan (default 7 hari).
 *
 * Kriteria HANYA berdasarkan kedaluwarsa, tidak berdasarkan "sudah dicabut". Token yang dicabut tetapi belum
 * kedaluwarsa harus tetap ada: deteksi pemakaian ulang token lama (rotasi) bergantung pada barisnya. Token yang
 * sudah kedaluwarsa selalu ditolak apa pun statusnya, jadi aman dibuang.
 */
@Slf4j
@Service
public class RefreshTokenCleanupService {

    static final int BATCH_SIZE = 1000;

    private final RefreshTokenCleanupRepository repository;
    private final Clock clock;
    private final Duration retention;

    public RefreshTokenCleanupService(RefreshTokenCleanupRepository repository, Clock clock,
                                      @Value("${interval.maintenance.refresh-token-retention:7d}") Duration retention) {
        this.repository = repository;
        this.clock = clock;
        this.retention = retention;
    }

    public int purgeExpired() {
        Instant cutoff = clock.instant().minus(retention);
        int total = 0;
        int deleted;
        do {
            deleted = repository.deleteExpiredBatch(cutoff, BATCH_SIZE);
            total += deleted;
        } while (deleted == BATCH_SIZE);

        if (total > 0) {
            log.info("Purged {} expired refresh token(s)", total);
        }
        return total;
    }
}
