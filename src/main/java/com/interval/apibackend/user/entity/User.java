package com.interval.apibackend.user.entity;

import com.interval.apibackend.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditableEntity {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public static User create(String fullName, String email, String passwordHash, String timezone) {
        User user = new User();
        user.fullName = fullName;
        user.email = email;
        user.passwordHash = passwordHash;
        user.timezone = timezone;
        return user;
    }
}
