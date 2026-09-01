package com.westy.codmanager.shipping.web;

import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.shipping.domain.CarrierEvent;
import com.westy.codmanager.shipping.domain.Shipment;

import java.time.Instant;
import java.util.List;

public final class ShippingDtos {

    private ShippingDtos() {
    }

    public record CarrierEventResponse(
            String rawStatus,
            String mappedStatus,
            Instant occurredAt) {

        static CarrierEventResponse from(CarrierEvent event) {
            return new CarrierEventResponse(event.getRawStatus(),
                    event.getMappedStatus(), event.getOccurredAt());
        }
    }

    public record ShipmentResponse(
            String id,
            String orderNumber,
            Carrier carrier,
            String trackingNumber,
            String labelUrl,
            String carrierStatus,
            String failureReason,
            Instant lastSyncedAt,
            List<CarrierEventResponse> events) {

        public static ShipmentResponse from(Shipment shipment) {
            return new ShipmentResponse(
                    shipment.getId().toString(),
                    shipment.getOrder().getOrderNumber(),
                    shipment.getCarrier(),
                    shipment.getTrackingNumber(),
                    shipment.getLabelUrl(),
                    shipment.getCarrierStatus(),
                    shipment.getFailureReason(),
                    shipment.getLastSyncedAt(),
                    shipment.getEvents().stream().map(CarrierEventResponse::from).toList());
        }
    }
}
