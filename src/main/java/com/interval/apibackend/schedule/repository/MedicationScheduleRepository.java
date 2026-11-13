package com.interval.apibackend.schedule.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.interval.apibackend.schedule.entity.MedicationSchedule;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Pencarian memakai Specification (lihat ScheduleSpecifications) agar kepemilikan dan filter opsional
 * tersusun tanpa "(:param is null or ...)". Pola itu berbahaya untuk parameter UUID: Hibernate bisa
 * mengikat nilainya sebagai BINARY(16), bukan CHAR(36), dan hasilnya kosong tanpa error.
 */
public interface MedicationScheduleRepository
        extends JpaRepository<MedicationSchedule, UUID>, JpaSpecificationExecutor<MedicationSchedule> {

    // Memuat jam dan obat sekaligus agar tidak terjadi N+1 (mengakses medication.getId() pada proxy lazy
    // tetap memicu query karena entity memakai field access).
    @Override
    @EntityGraph(attributePaths = {"times", "medication"})
    List<MedicationSchedule> findAll(Specification<MedicationSchedule> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"times", "medication"})
    Optional<MedicationSchedule> findOne(Specification<MedicationSchedule> spec);

    /** Dipakai saat obat dihapus: semua jadwalnya ikut di-soft-delete. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update MedicationSchedule s
               set s.deletedAt = :now, s.active = false, s.updatedAt = :now
             where s.medication.id = :medicationId and s.deletedAt is null
            """)
    int softDeleteByMedicationId(@Param("medicationId") UUID medicationId, @Param("now") Instant now);
}
