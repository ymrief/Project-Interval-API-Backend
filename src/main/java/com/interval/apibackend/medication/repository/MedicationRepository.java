package com.interval.apibackend.medication.repository;

import java.util.Optional;
import java.util.UUID;

import com.interval.apibackend.medication.entity.Medication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SEMUA query memakai userId (dari token) dan mengecualikan data yang sudah dihapus.
 * Tidak ada findById polos yang dipakai oleh service: itu akan membuka akses ke data orang lain.
 */
public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    Optional<Medication> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Page<Medication> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Page<Medication> findByUserIdAndActiveAndDeletedAtIsNull(UUID userId, boolean active, Pageable pageable);
}
