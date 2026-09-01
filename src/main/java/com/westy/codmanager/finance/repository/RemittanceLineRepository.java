package com.westy.codmanager.finance.repository;

import com.westy.codmanager.finance.domain.LineStatus;
import com.westy.codmanager.finance.domain.RemittanceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RemittanceLineRepository extends JpaRepository<RemittanceLine, UUID> {

    boolean existsByOrderIdAndStatus(UUID orderId, LineStatus status);
}
