package com.interval.apibackend.tracking.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.interval.apibackend.tracking.entity.LogStatus;
import com.interval.apibackend.tracking.entity.MedicationLog;

import jakarta.persistence.criteria.Predicate;

public final class LogSpecifications {

    private LogSpecifications() {
    }

    /**
     * Log milik pengguna pada rentang [from, until). status dan medicationId opsional (null = tanpa filter).
     * Memakai Specification, bukan "(:p is null or ...)", agar parameter UUID selalu terikat sebagai CHAR(36).
     */
    public static Specification<MedicationLog> filter(UUID userId, Instant from, Instant until,
                                                      LogStatus status, UUID medicationId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("scheduledAt"), from));
            predicates.add(cb.lessThan(root.<Instant>get("scheduledAt"), until));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (medicationId != null) {
                predicates.add(cb.equal(root.get("medicationId"), medicationId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
