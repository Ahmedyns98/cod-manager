package com.westy.codmanager.catalog.domain;

import com.westy.codmanager.common.entity.BaseEntity;
import com.westy.codmanager.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_variant")
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "size", length = 32)
    private String size;

    @Column(name = "color", length = 32)
    private String color;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "stock_qty", nullable = false)
    private int stockQty;

    protected ProductVariant() {
    }

    ProductVariant(Product product, String size, String color, String sku, int stockQty) {
        this.product = product;
        this.size = size;
        this.color = color;
        this.sku = sku;
        this.stockQty = stockQty;
    }

    /**
     * Stock lives on the variant and can never go negative. The check is here
     * rather than in a service so no caller can bypass it.
     */
    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "Quantity must be positive");
        }
        if (quantity > stockQty) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK",
                    "Only %d left of %s".formatted(stockQty, sku));
        }
        this.stockQty -= quantity;
    }

    public void restock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "Quantity must be positive");
        }
        this.stockQty += quantity;
    }

    public Product getProduct() {
        return product;
    }

    public String getSize() {
        return size;
    }

    public String getColor() {
        return color;
    }

    public String getSku() {
        return sku;
    }

    public int getStockQty() {
        return stockQty;
    }
}
