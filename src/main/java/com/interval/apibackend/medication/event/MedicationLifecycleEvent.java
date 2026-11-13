package com.interval.apibackend.medication.event;

import java.util.UUID;

/**
 * Dipublikasikan MedicationService saat obat dijeda, diaktifkan lagi, atau dihapus.
 *
 * Modul Schedule akan mendengarkannya dengan @EventListener biasa (sinkron, di transaksi yang sama):
 *  - PAUSED / DELETED : batalkan occurrence PENDING mendatang (CANCELLED); untuk DELETED juga
 *                       nonaktifkan dan soft-delete semua jadwal obat tersebut.
 *  - RESUMED          : buat ulang occurrence mendatang dari jadwal yang aktif.
 * Sinkron di transaksi yang sama menjamin "obat dihapus" dan "occurrence dibatalkan" atomik.
 */
public record MedicationLifecycleEvent(UUID userId, UUID medicationId, Type type) {

    public enum Type { PAUSED, RESUMED, DELETED }
}
