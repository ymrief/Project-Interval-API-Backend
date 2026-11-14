package com.interval.apibackend.tracking.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.interval.apibackend.schedule.entity.MedicationSchedule;

/**
 * Hanya id jadwal yang layak diproses job (aktif, belum dihapus, obatnya aktif dan belum dihapus, belum berakhir).
 * Keyset paging (id > afterId) lebih stabil dan murah daripada offset untuk iterasi seluruh tabel.
 */
public interface EligibleScheduleRepository extends Repository<MedicationSchedule, UUID> {

    @Query("""
            select s.id from MedicationSchedule s
             where s.id > :afterId
               and s.active = true and s.deletedAt is null
               and s.medication.active = true and s.medication.deletedAt is null
               and (s.endDate is null or s.endDate >= :earliestEnd)
             order by s.id
            """)
    List<UUID> findEligibleIdsAfter(@Param("afterId") UUID afterId,
                                    @Param("earliestEnd") LocalDate earliestEnd,
                                    Pageable pageable);
}
