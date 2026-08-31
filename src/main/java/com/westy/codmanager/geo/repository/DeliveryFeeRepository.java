package com.westy.codmanager.geo.repository;

import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.geo.domain.DeliveryFee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryFeeRepository extends JpaRepository<DeliveryFee, Long> {

    Optional<DeliveryFee> findByCarrierAndWilayaCode(Carrier carrier, Short wilayaCode);

    List<DeliveryFee> findByCarrierOrderByWilayaCodeAsc(Carrier carrier);
}
