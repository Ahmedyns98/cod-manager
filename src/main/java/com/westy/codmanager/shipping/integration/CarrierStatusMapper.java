package com.westy.codmanager.shipping.integration;

import com.westy.codmanager.order.domain.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Translates a carrier's vocabulary into this system's order statuses.
 *
 * Returning an Optional rather than a default is deliberate. A status nobody
 * has seen before must not silently become IN_TRANSIT: the raw value is stored,
 * the order is left alone, and a human decides what it meant.
 */
@Component
public class CarrierStatusMapper {

    private static final Map<String, OrderStatus> YALIDINE = Map.ofEntries(
            Map.entry("pas encore expédié", OrderStatus.PACKED),
            Map.entry("a vérifier", OrderStatus.PACKED),
            Map.entry("en préparation", OrderStatus.PACKED),
            Map.entry("pret a expedier", OrderStatus.SHIPPED),
            Map.entry("expédié", OrderStatus.SHIPPED),
            Map.entry("centre", OrderStatus.IN_TRANSIT),
            Map.entry("en localisation", OrderStatus.IN_TRANSIT),
            Map.entry("vers wilaya", OrderStatus.IN_TRANSIT),
            Map.entry("reçu à wilaya", OrderStatus.IN_TRANSIT),
            Map.entry("en attente du client", OrderStatus.OUT_FOR_DELIVERY),
            Map.entry("prêt pour livreur", OrderStatus.OUT_FOR_DELIVERY),
            Map.entry("sorti en livraison", OrderStatus.OUT_FOR_DELIVERY),
            Map.entry("livré", OrderStatus.DELIVERED),
            Map.entry("echèc livraison", OrderStatus.IN_TRANSIT),
            Map.entry("retour vers centre", OrderStatus.RETURNED),
            Map.entry("retourné au vendeur", OrderStatus.RETURNED),
            Map.entry("echange échoué", OrderStatus.RETURNED)
    );

    public Optional<OrderStatus> map(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(YALIDINE.get(rawStatus.trim().toLowerCase(Locale.ROOT)));
    }
}
