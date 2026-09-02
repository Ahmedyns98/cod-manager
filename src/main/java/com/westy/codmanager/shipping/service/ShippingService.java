package com.westy.codmanager.shipping.service;

import com.westy.codmanager.common.exception.BusinessRuleException;
import com.westy.codmanager.common.exception.NotFoundException;
import com.westy.codmanager.order.domain.Order;
import com.westy.codmanager.order.domain.OrderStatus;
import com.westy.codmanager.order.service.OrderService;
import com.westy.codmanager.order.state.OrderStateMachine;
import com.westy.codmanager.shipping.domain.Shipment;
import com.westy.codmanager.shipping.integration.CarrierClient;
import com.westy.codmanager.shipping.integration.CarrierException;
import com.westy.codmanager.shipping.integration.CarrierStatusMapper;
import com.westy.codmanager.shipping.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Hands orders to couriers and folds their status updates back in.
 *
 * Two rules hold throughout. A shipment row is written before the carrier is
 * called, so a timeout after the parcel was accepted leaves a record to
 * reconcile instead of a silent duplicate. And the carrier is a source of
 * information, not an authority: a status that maps to an illegal transition is
 * recorded and ignored, never forced onto the order.
 */
@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    private final ShipmentRepository shipments;
    private final OrderService orders;
    private final CarrierClientRegistry registry;
    private final CarrierStatusMapper mapper;
    private final OrderStateMachine stateMachine;

    public ShippingService(ShipmentRepository shipments, OrderService orders,
                           CarrierClientRegistry registry, CarrierStatusMapper mapper,
                           OrderStateMachine stateMachine) {
        this.shipments = shipments;
        this.orders = orders;
        this.registry = registry;
        this.mapper = mapper;
        this.stateMachine = stateMachine;
    }

    /**
     * Hands a packed order to its carrier.
     *
     * The shipment row is written before the API call and the unique constraint
     * on order_id makes a second call a no-op. If the carrier times out after
     * accepting the parcel, the row survives with its failure reason and the
     * retry reuses it instead of creating a duplicate.
     */
    @Transactional
    public Shipment ship(UUID ownerId, UUID orderId) {
        Order order = orders.get(ownerId, orderId);

        if (order.getStatus() != OrderStatus.PACKED) {
            throw new BusinessRuleException("NOT_PACKED",
                    "Only a PACKED order can be shipped, this one is " + order.getStatus());
        }

        Shipment shipment = shipments.findByOrderId(orderId)
                .orElseGet(() -> shipments.save(new Shipment(order, order.getCarrier())));

        if (shipment.isRegistered()) {
            return shipment;
        }

        CarrierClient client = registry.forCarrier(order.getCarrier());

        try {
            CarrierClient.ParcelCreated created = client.createParcel(order);
            shipment.markCreated(created.trackingNumber(), created.labelUrl());
        } catch (CarrierException ex) {
            shipment.markFailed(ex.getMessage());
            throw ex;
        }

        orders.transition(ownerId, orderId, OrderStatus.SHIPPED,
                "Handed to " + order.getCarrier() + " as " + shipment.getTrackingNumber());

        return shipment;
    }

    /**
     * Pulls the current status for one shipment and, when the carrier's status
     * maps to a legal next state, advances the order.
     *
     * An unknown or out-of-order status is recorded and otherwise ignored. The
     * carrier is a source of information, not an authority over our state
     * machine.
     */
    @Transactional
    public Shipment sync(UUID ownerId, UUID orderId) {
        Order order = orders.get(ownerId, orderId);

        Shipment shipment = shipments.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Shipment for order", orderId));

        if (!shipment.isRegistered()) {
            throw new BusinessRuleException("NOT_REGISTERED",
                    "This shipment was never accepted by the carrier");
        }

        CarrierClient client = registry.forCarrier(shipment.getCarrier());
        CarrierClient.ParcelStatus status = client.fetchStatus(shipment.getTrackingNumber());

        Optional<OrderStatus> mapped = mapper.map(status.rawStatus());

        shipment.recordEvent(status.rawStatus(),
                mapped.map(Enum::name).orElse(null), status.rawPayload());

        if (mapped.isEmpty()) {
            log.warn("Unmapped carrier status '{}' on parcel {}",
                    status.rawStatus(), shipment.getTrackingNumber());
            return shipment;
        }

        OrderStatus next = mapped.get();

        if (next != order.getStatus() && stateMachine.canTransition(order.getStatus(), next)) {
            orders.transition(ownerId, orderId, next, "Carrier reported: " + status.rawStatus());
        }

        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment get(UUID ownerId, UUID orderId) {
        orders.get(ownerId, orderId);

        return shipments.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Shipment for order", orderId));
    }
}
