package com.westy.codmanager.order.domain;

import com.westy.codmanager.catalog.domain.ProductVariant;
import com.westy.codmanager.common.entity.BaseEntity;
import com.westy.codmanager.customer.domain.Customer;
import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.geo.domain.DeliveryType;
import com.westy.codmanager.geo.domain.Wilaya;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "order_number", nullable = false, length = 24)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private OrderSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 16)
    private DeliveryType deliveryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier", nullable = false, length = 32)
    private Carrier carrier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wilaya_code", nullable = false)
    private Wilaya wilaya;

    @Column(name = "commune", nullable = false, length = 120)
    private String commune;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected Order() {
    }

    public Order(UUID ownerId, String orderNumber, Customer customer, OrderSource source,
                 DeliveryType deliveryType, Carrier carrier, String commune,
                 String address, String notes) {
        this.ownerId = ownerId;
        this.orderNumber = orderNumber;
        this.customer = customer;
        this.source = source;
        this.deliveryType = deliveryType;
        this.carrier = carrier;
        this.wilaya = customer.getWilaya();
        this.commune = commune;
        this.address = address;
        this.notes = notes;

        history.add(new OrderStatusHistory(this, null, OrderStatus.PENDING, "Order created", ownerId));
    }

    public OrderItem addItem(ProductVariant variant, int quantity) {
        OrderItem item = new OrderItem(this, variant, quantity);
        items.add(item);
        return item;
    }

    /**
     * Recomputes the money from the lines. Called after items change and after
     * the delivery fee is resolved, so the three columns can never drift apart.
     */
    public void recalculate(BigDecimal deliveryFee) {
        this.subtotal = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.deliveryFee = deliveryFee;
        this.total = subtotal.add(deliveryFee);
    }

    /**
     * Applies an already-validated transition. Validation lives in
     * OrderStateMachine; this method only records the consequences.
     */
    public void transitionTo(OrderStatus next, String reason, UUID changedBy) {
        OrderStatus previous = this.status;
        this.status = next;

        if (next == OrderStatus.CONFIRMED && confirmedAt == null) {
            this.confirmedAt = Instant.now();
        }
        if (next == OrderStatus.DELIVERED && deliveredAt == null) {
            this.deliveredAt = Instant.now();
        }

        history.add(new OrderStatusHistory(this, previous, next, reason, changedBy));
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public OrderSource getSource() {
        return source;
    }

    public DeliveryType getDeliveryType() {
        return deliveryType;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public Wilaya getWilaya() {
        return wilaya;
    }

    public String getCommune() {
        return commune;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public List<OrderStatusHistory> getHistory() {
        return List.copyOf(history);
    }
}
