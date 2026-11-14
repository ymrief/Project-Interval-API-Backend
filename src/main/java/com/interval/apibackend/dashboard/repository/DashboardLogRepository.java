package com.interval.apibackend.dashboard.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;

public interface DashboardLogRepository extends Repository<MedicationLog, UUID> {

    /** Log pengguna pada [rangeStart, rangeEnd) kecuali satu status, urut waktu naik (id sebagai pembeda). */
    @Query("""
            select l from MedicationLog l
             where l.userId = :userId and l.scheduledAt >= :rangeStart and l.scheduledAt < :rangeEnd
               and l.status <> :excluded
             order by l.scheduledAt asc, l.id asc
            """)
    List<MedicationLog> findForRange(@Param("userId") UUID userId,
                                     @Param("rangeStart") Instant rangeStart,
                                     @Param("rangeEnd") Instant rangeEnd,
                                     @Param("excluded") LogStatus excluded);
}
