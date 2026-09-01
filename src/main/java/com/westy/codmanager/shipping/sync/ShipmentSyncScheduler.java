package com.westy.codmanager.shipping.sync;

import com.westy.codmanager.order.domain.Order;
import com.westy.codmanager.shipping.domain.Shipment;
import com.westy.codmanager.shipping.repository.ShipmentRepository;
import com.westy.codmanager.shipping.service.ShippingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the carriers for shipments that have not reached a final state.
 *
 * Webhooks are the primary channel; this is the safety net for the ones that
 * never arrive. Between the two, a missed notification costs one poll interval
 * rather than a wrong status forever.
 */
@Component
public class ShipmentSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShipmentSyncScheduler.class);

    private final ShipmentRepository shipments;
    private final ShippingService shipping;
    private final long pauseMillis;

    public ShipmentSyncScheduler(ShipmentRepository shipments, ShippingService shipping,
                                 @Value("${app.sync.pause-millis:250}") long pauseMillis) {
        this.shipments = shipments;
        this.shipping = shipping;
        this.pauseMillis = pauseMillis;
    }

    @Scheduled(fixedDelayString = "${app.sync.interval:PT15M}", initialDelayString = "PT1M")
    public void syncOpenShipments() {
        List<Shipment> open = shipments.findByTrackingNumberIsNotNull().stream()
                .filter(shipment -> !shipment.getOrder().getStatus().isTerminal())
                .toList();

        if (open.isEmpty()) {
            return;
        }

        log.info("Syncing {} open shipment(s)", open.size());

        int updated = 0;
        int failed = 0;

        for (Shipment shipment : open) {
            try {
                syncOne(shipment);
                updated++;
            } catch (Exception ex) {
                /*
                 * One carrier error must not abort the batch: the remaining
                 * parcels would then go unsynced until the next interval.
                 */
                failed++;
                log.warn("Sync failed for parcel {}: {}",
                        shipment.getTrackingNumber(), ex.getMessage());
            }

            pause();
        }

        log.info("Sync finished: {} updated, {} failed", updated, failed);
    }

    @Transactional
    protected void syncOne(Shipment shipment) {
        Order order = shipment.getOrder();
        shipping.sync(order.getOwnerId(), order.getId());
    }

    /** Carriers rate-limit aggressively; a short gap keeps the batch welcome. */
    private void pause() {
        if (pauseMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(pauseMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
