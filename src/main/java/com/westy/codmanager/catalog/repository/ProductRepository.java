package com.westy.codmanager.catalog.repository;

import com.westy.codmanager.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByOwnerId(UUID ownerId, Pageable pageable);

    /* Scoped by owner so one seller can never reach another seller's product. */
    Optional<Product> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndSku(UUID ownerId, String sku);
}
