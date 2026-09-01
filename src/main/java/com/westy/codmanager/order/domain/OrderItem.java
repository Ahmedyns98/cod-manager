package com.westy.codmanager.order.domain;

import com.westy.codmanager.catalog.domain.ProductVariant;
import com.westy.codmanager.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A line on an order.
 *
 * The product name, SKU and unit price are copied in at creation time rather
 * than read through the variant association. Renaming a product or changing
 * its price six months from now must not rewrite what a customer actually
 * agreed to pay.
 */
@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "variant_sku", nullable = false, length = 64)
    private String variantSku;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected OrderItem() {
    }

    OrderItem(Order order, ProductVariant variant, int quantity) {
        this.order = order;
        this.variant = variant;
        this.quantity = quantity;
        this.productName = variant.getProduct().getName();
        this.variantSku = variant.getSku();
        this.unitPrice = variant.getProduct().getBasePrice();
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Order getOrder() {
        return order;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public String getProductName() {
        return productName;
    }

    public String getVariantSku() {
        return variantSku;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}
