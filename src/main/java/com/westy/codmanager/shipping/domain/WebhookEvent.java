package com.westy.codmanager.shipping.domain;

import com.westy.codmanager.geo.domain.Carrier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A raw inbound notification, stored before anything is done with it.
 *
 * Writing first and interpreting second means a bug in the handler costs
 * nothing: the payload is on disk and can be replayed once the bug is fixed.
 */
@Entity
@Table(name = "webhook_event")
public class WebhookEvent {

    /* Assigned up front, for the same reason as BaseEntity. */
    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier", nullable = false, length = 32)
    private Carrier carrier;

    /** Hash of the payload. Two identical deliveries produce the same value. */
    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @Column(name = "tracking", length = 64)
    private String tracking;

    @Column(name = "raw_status", length = 120)
    private String rawStatus;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "process_error", length = 500)
    private String processError;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WebhookEvent() {
    }

    public WebhookEvent(Carrier carrier, String fingerprint, String tracking,
                        String rawStatus, String payload) {
        this.carrier = carrier;
        this.fingerprint = fingerprint;
        this.tracking = tracking;
        this.rawStatus = rawStatus;
        this.payload = payload;
        this.receivedAt = Instant.now();
    }

    public void markProcessed() {
        this.processed = true;
        this.processError = null;
    }

    public void markFailed(String error) {
        this.processed = false;
        this.processError = error == null ? null
                : error.substring(0, Math.min(error.length(), 500));
    }

    public UUID getId() {
        return id;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getTracking() {
        return tracking;
    }

    public String getRawStatus() {
        return rawStatus;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isProcessed() {
        return processed;
    }

    public String getProcessError() {
        return processError;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
