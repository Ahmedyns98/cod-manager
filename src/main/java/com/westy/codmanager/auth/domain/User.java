package com.westy.codmanager.auth.domain;

import com.westy.codmanager.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash. The plain password never exists outside AuthService. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "store_name", nullable = false, length = 120)
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role = Role.OWNER;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected User() {
        // required by JPA
    }

    public User(String email, String passwordHash, String storeName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.storeName = storeName;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getStoreName() {
        return storeName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void deactivate() {
        this.active = false;
    }
}
