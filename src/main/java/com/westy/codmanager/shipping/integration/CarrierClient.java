package com.westy.codmanager.shipping.integration;

import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.order.domain.Order;

/**
 * What every courier must be able to do, expressed in this system's language
 * rather than any one carrier's.
 *
 * Adding a courier means adding one implementation of this interface and
 * nothing else: no service, controller or scheduler changes.
 */
public interface CarrierClient {

    Carrier carrier();

    /**
     * Registers a parcel and returns its tracking number.
     *
     * Implementations must be idempotent on the order number. A retry after a
     * timeout must not create a second parcel for the same order.
     */
    ParcelCreated createParcel(Order order);

    ParcelStatus fetchStatus(String trackingNumber);

    void cancelParcel(String trackingNumber);

    record ParcelCreated(String trackingNumber, String labelUrl) {
    }

    record ParcelStatus(String rawStatus, String rawPayload) {
    }
}
