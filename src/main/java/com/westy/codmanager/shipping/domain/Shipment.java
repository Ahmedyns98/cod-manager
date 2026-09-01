package com.westy.codmanager.shipping.domain;

import com.westy.codmanager.common.entity.BaseEntity;
import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.order.domain.Order;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shipment")
public class Shipment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier", nullable = false, length = 32)
    private Carrier carrier;

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(name = "label_url", length = 500)
    private String labelUrl;

    @Column(name = "carrier_status", length = 120)
    private String carrierStatus;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CarrierEvent> events = new ArrayList<>();

    protected Shipment() {
    }

    public Shipment(Order order, Carrier carrier) {
        this.order = order;
        this.carrier = carrier;
    }

    public void markCreated(String trackingNumber, String labelUrl) {
        this.trackingNumber = trackingNumber;
        this.labelUrl = labelUrl;
        this.failureReason = null;
        this.lastSyncedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.failureReason = reason;
    }

    public CarrierEvent recordEvent(String rawStatus, String mappedStatus, String payload) {
        this.carrierStatus = rawStatus;
        this.lastSyncedAt = Instant.now();

        CarrierEvent event = new CarrierEvent(this, rawStatus, mappedStatus, payload);
        events.add(event);

        return event;
    }

    /** A shipment without a tracking number was never accepted by the carrier. */
    public boolean isRegistered() {
        return trackingNumber != null;
    }

    public Order getOrder() {
        return order;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getLabelUrl() {
        return labelUrl;
    }

    public String getCarrierStatus() {
        return carrierStatus;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public List<CarrierEvent> getEvents() {
        return List.copyOf(events);
    }
}
