package com.interval.apibackend.tracking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;

public interface MedicationLogRepository
        extends JpaRepository<MedicationLog, UUID>, JpaSpecificationExecutor<MedicationLog> {

    /** Proyeksi hasil GROUP BY status. */
    interface StatusCount {
        LogStatus getStatus();

        long getTotal();
    }

    // ------------------------------------------------------------ dipakai occurrence generator

    /** Dipakai generator untuk menyelaraskan occurrence satu jadwal (semua status). */
    List<MedicationLog> findByScheduleIdAndScheduledAtGreaterThanEqual(UUID scheduleId, Instant since);

    /**
     * Mengubah status massal untuk satu obat (mis. PENDING -> CANCELLED saat obat dijeda/dihapus).
     * Tanpa clearAutomatically: persistence context milik pemanggil tidak boleh dikosongkan di tengah transaksinya.
     * updatedAt diisi manual karena @UpdateTimestamp tidak berlaku pada bulk update.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update MedicationLog l
               set l.status = :toStatus, l.updatedAt = :now
             where l.medicationId = :medicationId and l.status = :fromStatus and l.scheduledAt >= :since
            """)
    int transitionByMedication(@Param("medicationId") UUID medicationId,
                               @Param("fromStatus") LogStatus fromStatus,
                               @Param("toStatus") LogStatus toStatus,
                               @Param("since") Instant since,
                               @Param("now") Instant now);

    /** PENDING yang terlewat dari batas toleransi -> MISSED. Perbandingan sengaja ketat (<). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update MedicationLog l
               set l.status = :toStatus, l.updatedAt = :now
             where l.status = :fromStatus and l.scheduledAt < :cutoff
            """)
    int transitionOverdue(@Param("fromStatus") LogStatus fromStatus,
                          @Param("toStatus") LogStatus toStatus,
                          @Param("cutoff") Instant cutoff,
                          @Param("now") Instant now);

    // ------------------------------------------------------------ dipakai modul Tracking

    /** Log milik pengguna; milik orang lain diperlakukan sama dengan tidak ada (404). */
    Optional<MedicationLog> findByIdAndUserId(UUID id, UUID userId);

    /** Jumlah log per status pada rentang [rangeStart, rangeEnd). Dipakai adherence dan dashboard. */
    @Query("""
            select l.status as status, count(l) as total
              from MedicationLog l
             where l.userId = :userId and l.scheduledAt >= :rangeStart and l.scheduledAt < :rangeEnd
             group by l.status
            """)
    List<StatusCount> countByStatus(@Param("userId") UUID userId,
                                    @Param("rangeStart") Instant rangeStart,
                                    @Param("rangeEnd") Instant rangeEnd);

    @Query("""
            select l.status as status, count(l) as total
              from MedicationLog l
             where l.userId = :userId and l.medicationId = :medicationId
               and l.scheduledAt >= :rangeStart and l.scheduledAt < :rangeEnd
             group by l.status
            """)
    List<StatusCount> countByStatusForMedication(@Param("userId") UUID userId,
                                                 @Param("medicationId") UUID medicationId,
                                                 @Param("rangeStart") Instant rangeStart,
                                                 @Param("rangeEnd") Instant rangeEnd);
}