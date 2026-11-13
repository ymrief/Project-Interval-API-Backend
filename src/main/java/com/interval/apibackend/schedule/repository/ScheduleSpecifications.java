package com.interval.apibackend.schedule.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.interval.apibackend.medication.entity.Medication;
import com.interval.apibackend.schedule.entity.MedicationSchedule;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

public final class ScheduleSpecifications {

    private ScheduleSpecifications() {
    }

    /**
     * Jadwal milik pengguna (lewat obatnya), belum dihapus, dan obatnya belum dihapus.
     * medicationId dan active opsional (null = tanpa filter).
     */
    public static Specification<MedicationSchedule> owned(UUID userId, UUID medicationId, Boolean active) {
        return (root, query, cb) -> {
            Join<MedicationSchedule, Medication> medication = root.join("medication");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(medication.get("userId"), userId));
            predicates.add(cb.isNull(medication.get("deletedAt")));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (medicationId != null) {
                predicates.add(cb.equal(medication.get("id"), medicationId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<MedicationSchedule> withId(UUID id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }
}
