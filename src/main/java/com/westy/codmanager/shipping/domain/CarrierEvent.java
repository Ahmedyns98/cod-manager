package com.westy.codmanager.shipping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One status update as the carrier sent it.
 *
 * raw_status is stored untranslated on purpose. Carriers add statuses without
 * warning, and keeping the original means an unknown value is a data question
 * later rather than lost information now.
 */
@Entity
@Table(name = "carrier_event")
public class CarrierEvent {

    /* Assigned up front, for the same reason as BaseEntity. */
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(name = "raw_status", nullable = false, length = 120)
    private String rawStatus;

    @Column(name = "mapped_status", length = 32)
    private String mappedStatus;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CarrierEvent() {
    }

    CarrierEvent(Shipment shipment, String rawStatus, String mappedStatus, String payload) {
        this.shipment = shipment;
        this.rawStatus = rawStatus;
        this.mappedStatus = mappedStatus;
        this.payload = payload;
        this.occurredAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getRawStatus() {
        return rawStatus;
    }

    public String getMappedStatus() {
        return mappedStatus;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
