package com.interval.apibackend.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.interval.apibackend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Email sudah dinormalisasi (trim + lowercase); collation database juga case-insensitive. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Hanya mengambil satu kolom, tanpa memuat seluruh entity. */
    @Query("select u.timezone from User u where u.id = :id")
    Optional<String> findTimezoneById(@Param("id") UUID id);
}
