package com.westy.codmanager.shipping.repository;

import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.shipping.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByCarrierAndFingerprint(Carrier carrier, String fingerprint);

    boolean existsByCarrierAndFingerprint(Carrier carrier, String fingerprint);
}
