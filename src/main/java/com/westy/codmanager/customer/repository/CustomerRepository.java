package com.westy.codmanager.customer.repository;

import com.westy.codmanager.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByOwnerIdAndPhone(UUID ownerId, String phone);
}
