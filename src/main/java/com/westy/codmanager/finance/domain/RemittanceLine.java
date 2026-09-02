package com.westy.codmanager.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "remittance_line")
public class RemittanceLine {

    /* Assigned up front, for the same reason as BaseEntity. */
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remittance_id", nullable = false)
    private Remittance remittance;

    /** Null when the tracking number matched nothing in this account. */
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "tracking", nullable = false, length = 64)
    private String tracking;

    @Column(name = "collected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal collectedAmount;

    @Column(name = "carrier_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal carrierFee = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    /** The order total as this system recorded it, for comparison. */
    @Column(name = "expected_amount", precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private LineStatus status;

    @Column(name = "note", length = 400)
    private String note;

    @Column(name = "source_row")
    private Integer sourceRow;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RemittanceLine() {
    }

    public RemittanceLine(Remittance remittance, UUID orderId, String tracking,
                          BigDecimal collectedAmount, BigDecimal carrierFee,
                          BigDecimal expectedAmount, LineStatus status,
                          String note, Integer sourceRow) {
        this.remittance = remittance;
        this.orderId = orderId;
        this.tracking = tracking;
        this.collectedAmount = collectedAmount;
        this.carrierFee = carrierFee == null ? BigDecimal.ZERO : carrierFee;
        this.netAmount = collectedAmount.subtract(this.carrierFee);
        this.expectedAmount = expectedAmount;
        this.status = status;
        this.note = note;
        this.sourceRow = sourceRow;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getTracking() {
        return tracking;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public BigDecimal getCarrierFee() {
        return carrierFee;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public LineStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Integer getSourceRow() {
        return sourceRow;
    }
}
