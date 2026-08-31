package com.westy.codmanager.geo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "delivery_fee")
public class DeliveryFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier", nullable = false, length = 32)
    private Carrier carrier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wilaya_code", nullable = false)
    private Wilaya wilaya;

    /** Money is always BigDecimal with scale 2. Never a floating point type. */
    @Column(name = "home_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal homePrice;

    @Column(name = "stopdesk_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal stopdeskPrice;

    protected DeliveryFee() {
    }

    public Long getId() {
        return id;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public Wilaya getWilaya() {
        return wilaya;
    }

    public BigDecimal getHomePrice() {
        return homePrice;
    }

    public BigDecimal getStopdeskPrice() {
        return stopdeskPrice;
    }

    public BigDecimal priceFor(DeliveryType type) {
        return type == DeliveryType.STOPDESK ? stopdeskPrice : homePrice;
    }
}
