package com.westy.codmanager.catalog.domain;

import com.westy.codmanager.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    /**
     * Stored as a plain id rather than a User association. Ownership is a
     * scoping concern, and keeping it flat avoids dragging the whole auth
     * aggregate into every catalog query.
     */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {
    }

    public Product(UUID ownerId, String name, String sku,
                   BigDecimal basePrice, BigDecimal costPrice) {
        this.ownerId = ownerId;
        this.name = name;
        this.sku = sku;
        this.basePrice = basePrice;
        this.costPrice = costPrice == null ? BigDecimal.ZERO : costPrice;
    }

    public void update(String name, BigDecimal basePrice, BigDecimal costPrice, boolean active) {
        this.name = name;
        this.basePrice = basePrice;
        this.costPrice = costPrice == null ? BigDecimal.ZERO : costPrice;
        this.active = active;
    }

    public ProductVariant addVariant(String size, String color, String sku, int stockQty) {
        ProductVariant variant = new ProductVariant(this, size, color, sku, stockQty);
        variants.add(variant);
        return variant;
    }

    /** Gross margin per unit, useful before the analytics module exists. */
    public BigDecimal unitMargin() {
        return basePrice.subtract(costPrice);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public boolean isActive() {
        return active;
    }

    public List<ProductVariant> getVariants() {
        return List.copyOf(variants);
    }
}
