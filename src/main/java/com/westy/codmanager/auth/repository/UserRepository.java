package com.westy.codmanager.auth.repository;

import com.westy.codmanager.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Accounts are looked up by email, which is the login identity. The address is
 * normalised to lower case before it is ever stored or queried, so these two
 * methods can rely on an exact match rather than a case-insensitive scan.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
