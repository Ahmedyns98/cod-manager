package com.westy.codmanager.finance.repository;

import com.westy.codmanager.finance.domain.Remittance;
import com.westy.codmanager.geo.domain.Carrier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RemittanceRepository extends JpaRepository<Remittance, UUID> {

    Optional<Remittance> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Remittance> findByOwnerIdOrderByReceivedAtDesc(UUID ownerId, Pageable pageable);

    boolean existsByOwnerIdAndCarrierAndReference(UUID ownerId, Carrier carrier, String reference);
}
