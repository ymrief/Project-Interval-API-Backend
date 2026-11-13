package com.interval.apibackend.schedule.event;

import java.util.UUID;

/**
 * Dipublikasikan ScheduleService setiap kali aturan jadwal benar-benar berubah.
 *
 * Occurrence generator (langkah berikutnya) mendengarkannya dengan @EventListener biasa
 * (sinkron, transaksi yang sama):
 *  - CREATED : buat occurrence PENDING untuk horizon ke depan
 *  - UPDATED : batalkan occurrence PENDING mendatang milik jadwal ini (CANCELLED), lalu buat ulang
 *  - DELETED : batalkan occurrence PENDING mendatang
 * Event tidak diterbitkan jika PATCH tidak mengubah apa pun.
 */
public record ScheduleChangedEvent(UUID userId, UUID medicationId, UUID scheduleId, Type type) {

    public enum Type { CREATED, UPDATED, DELETED }
}
