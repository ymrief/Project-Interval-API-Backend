package com.interval.apibackend.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.interval.apibackend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Email sudah dinormalisasi (trim + lowercase); collation database juga case-insensitive. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
