package com.westy.codmanager.shipping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.order.domain.Order;
import com.westy.codmanager.order.domain.OrderStatus;
import com.westy.codmanager.order.service.OrderService;
import com.westy.codmanager.order.state.OrderStateMachine;
import com.westy.codmanager.shipping.domain.Shipment;
import com.westy.codmanager.shipping.domain.WebhookEvent;
import com.westy.codmanager.shipping.integration.CarrierStatusMapper;
import com.westy.codmanager.shipping.repository.ShipmentRepository;
import com.westy.codmanager.shipping.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEventRepository events;
    private final ShipmentRepository shipments;
    private final OrderService orders;
    private final CarrierStatusMapper mapper;
    private final OrderStateMachine stateMachine;
    private final WebhookSignatureVerifier verifier;
    private final ObjectMapper json;

    public WebhookService(WebhookEventRepository events, ShipmentRepository shipments,
                          OrderService orders, CarrierStatusMapper mapper,
                          OrderStateMachine stateMachine, WebhookSignatureVerifier verifier,
                          ObjectMapper json) {
        this.events = events;
        this.shipments = shipments;
        this.orders = orders;
        this.mapper = mapper;
        this.stateMachine = stateMachine;
        this.verifier = verifier;
        this.json = json;
    }

    /**
     * Records and applies one inbound notification.
     *
     * Returns quietly when the payload has been seen before. Carriers resend
     * until they get a 2xx, so a duplicate is the normal case, not an error to
     * report back.
     */
    @Transactional
    public Result handle(Carrier carrier, String rawBody) {
        String fingerprint = verifier.fingerprint(rawBody);

        if (events.existsByCarrierAndFingerprint(carrier, fingerprint)) {
            log.debug("Ignoring repeat {} webhook {}", carrier, fingerprint);
            return Result.DUPLICATE;
        }

        JsonNode node = parse(rawBody);
        String tracking = text(node, "tracking");
        String rawStatus = text(node, "status");

        WebhookEvent event = events.save(
                new WebhookEvent(carrier, fingerprint, tracking, rawStatus, rawBody));

        if (tracking == null || rawStatus == null) {
            event.markFailed("Payload has no tracking number or status");
            return Result.IGNORED;
        }

        Optional<Shipment> found = shipments.findByTrackingNumber(tracking);

        if (found.isEmpty()) {
            /*
             * Not an error worth failing on: the parcel may belong to another
             * system sharing the carrier account. It stays on record either way.
             */
            event.markFailed("No shipment matches tracking " + tracking);
            return Result.IGNORED;
        }

        applyStatus(found.get(), rawStatus, rawBody);
        event.markProcessed();

        return Result.APPLIED;
    }

    private void applyStatus(Shipment shipment, String rawStatus, String rawBody) {
        Optional<OrderStatus> mapped = mapper.map(rawStatus);

        shipment.recordEvent(rawStatus, mapped.map(Enum::name).orElse(null), rawBody);

        if (mapped.isEmpty()) {
            log.warn("Unmapped webhook status '{}' on parcel {}",
                    rawStatus, shipment.getTrackingNumber());
            return;
        }

        Order order = shipment.getOrder();
        OrderStatus next = mapped.get();

        if (next != order.getStatus() && stateMachine.canTransition(order.getStatus(), next)) {
            orders.transition(order.getOwnerId(), order.getId(), next,
                    "Carrier webhook: " + rawStatus);
        } else if (next != order.getStatus()) {
            log.info("Webhook wanted {} -> {} on order {}, which the state machine forbids",
                    order.getStatus(), next, order.getOrderNumber());
        }
    }

    private JsonNode parse(String rawBody) {
        try {
            return json.readTree(rawBody);
        } catch (Exception ex) {
            return json.createObjectNode();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    public enum Result {
        APPLIED,
        DUPLICATE,
        IGNORED
    }
}
