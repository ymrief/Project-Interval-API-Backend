package com.interval.apibackend.dashboard.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.interval.apibackend.medication.entity.Medication;

public interface DashboardMedicationRepository extends Repository<Medication, UUID> {

    /** Obat yang aktif (tidak dijeda) dan belum dihapus. */
    long countByUserIdAndActiveTrueAndDeletedAtIsNull(UUID userId);

    /** Dipakai untuk melengkapi dosage dan instructions; id berasal dari log milik pengguna itu sendiri. */
    List<Medication> findByIdIn(Collection<UUID> ids);
}
