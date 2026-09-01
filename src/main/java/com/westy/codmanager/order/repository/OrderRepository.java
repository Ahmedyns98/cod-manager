package com.westy.codmanager.order.repository;

import com.westy.codmanager.order.domain.Order;
import com.westy.codmanager.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Order> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<Order> findByOwnerIdAndStatus(UUID ownerId, OrderStatus status, Pageable pageable);

    /* Sequence for the human-readable order number, scoped to one seller. */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.ownerId = :ownerId")
    long countByOwner(UUID ownerId);
}
