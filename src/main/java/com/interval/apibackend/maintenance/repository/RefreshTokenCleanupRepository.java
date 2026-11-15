package com.interval.apibackend.maintenance.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.interval.apibackend.auth.entity.RefreshToken;

public interface RefreshTokenCleanupRepository extends Repository<RefreshToken, UUID> {

    /**
     * Menghapus maksimal {@code batch} token yang SUDAH KEDALUWARSA sebelum cutoff. Per batch (tiap panggilan satu
     * transaksi) agar tidak mengunci tabel lama. Native karena JPQL tidak mendukung LIMIT pada DELETE.
     */
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM refresh_tokens WHERE expires_at < :cutoff LIMIT :batch", nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") Instant cutoff, @Param("batch") int batch);
}
