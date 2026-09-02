package com.westy.codmanager.order.service;

import com.westy.codmanager.catalog.domain.ProductVariant;
import com.westy.codmanager.catalog.repository.ProductVariantRepository;
import com.westy.codmanager.common.exception.BusinessRuleException;
import com.westy.codmanager.common.exception.NotFoundException;
import com.westy.codmanager.customer.domain.Customer;
import com.westy.codmanager.customer.repository.CustomerRepository;
import com.westy.codmanager.geo.domain.DeliveryFee;
import com.westy.codmanager.geo.domain.Wilaya;
import com.westy.codmanager.geo.repository.DeliveryFeeRepository;
import com.westy.codmanager.geo.repository.WilayaRepository;
import com.westy.codmanager.order.domain.Order;
import com.westy.codmanager.order.domain.OrderItem;
import com.westy.codmanager.order.domain.OrderStatus;
import com.westy.codmanager.order.repository.OrderRepository;
import com.westy.codmanager.order.state.OrderStateMachine;
import com.westy.codmanager.order.web.OrderDtos.CreateOrderRequest;
import com.westy.codmanager.order.web.OrderDtos.OrderLineRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Creation and lifecycle of orders.
 *
 * Every status change in the system passes through transition(), which is the
 * only place stock moves and the only place history is written. A single path
 * is what makes the lifecycle trustworthy: there is no second way for an order
 * to reach a state nobody expected.
 *
 * Orders are priced at creation from the destination wilaya and the delivery
 * type, and those figures are stored on the order. A tariff change next month
 * must not rewrite what a customer already agreed to pay.
 */
@Service
public class OrderService {

    private static final ZoneId ALGIERS = ZoneId.of("Africa/Algiers");
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final ProductVariantRepository variants;
    private final WilayaRepository wilayas;
    private final DeliveryFeeRepository fees;
    private final OrderStateMachine stateMachine;

    public OrderService(OrderRepository orders, CustomerRepository customers,
                        ProductVariantRepository variants, WilayaRepository wilayas,
                        DeliveryFeeRepository fees, OrderStateMachine stateMachine) {
        this.orders = orders;
        this.customers = customers;
        this.variants = variants;
        this.wilayas = wilayas;
        this.fees = fees;
        this.stateMachine = stateMachine;
    }

    @Transactional
    public Order create(UUID ownerId, CreateOrderRequest request) {
        Wilaya wilaya = wilayas.findById(request.wilayaCode())
                .orElseThrow(() -> new NotFoundException("Wilaya", request.wilayaCode()));

        Customer customer = findOrCreateCustomer(ownerId, request, wilaya);

        if (customer.isBlacklisted()) {
            throw new BusinessRuleException("CUSTOMER_BLACKLISTED",
                    "This customer is blacklisted and cannot order");
        }

        Order order = new Order(ownerId, nextOrderNumber(ownerId), customer, request.source(),
                request.deliveryType(), request.carrier(), request.commune().trim(),
                request.address(), request.notes());

        for (OrderLineRequest line : request.items()) {
            ProductVariant variant = variants.findById(line.variantId())
                    .orElseThrow(() -> new NotFoundException("Variant", line.variantId()));

            if (!variant.getProduct().getOwnerId().equals(ownerId)) {
                throw new NotFoundException("Variant", line.variantId());
            }

            order.addItem(variant, line.quantity());
        }

        order.recalculate(resolveDeliveryFee(order));

        return orders.save(order);
    }

    @Transactional(readOnly = true)
    public Order get(UUID ownerId, UUID orderId) {
        return orders.findByIdAndOwnerId(orderId, ownerId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    @Transactional(readOnly = true)
    public Page<Order> list(UUID ownerId, OrderStatus status, Pageable pageable) {
        return status == null
                ? orders.findByOwnerId(ownerId, pageable)
                : orders.findByOwnerIdAndStatus(ownerId, status, pageable);
    }

    /**
     * The only way an order changes status.
     *
     * Stock is committed when the order reaches CONFIRMED, not when it is
     * created: a large share of COD orders never get confirmed because the
     * customer does not pick up, and reserving on creation would show items as
     * sold out while they sit on the shelf.
     */
    @Transactional
    public Order transition(UUID ownerId, UUID orderId, OrderStatus next, String reason) {
        Order order = get(ownerId, orderId);
        OrderStatus current = order.getStatus();

        stateMachine.assertTransition(current, next);

        if (!current.holdsStock() && next.holdsStock()) {
            order.getItems().forEach(item -> item.getVariant().reserve(item.getQuantity()));
        } else if (current.holdsStock() && !next.holdsStock()) {
            order.getItems().forEach(item -> item.getVariant().restock(item.getQuantity()));
        }

        if (next == OrderStatus.DELIVERED) {
            order.getCustomer().recordDelivery();
        } else if (next == OrderStatus.RETURNED) {
            order.getCustomer().recordReturn();
        }

        order.transitionTo(next, reason, ownerId);

        return order;
    }

    public java.util.List<OrderStatus> nextStates(Order order) {
        return stateMachine.nextStates(order.getStatus()).stream().sorted().toList();
    }

    private Customer findOrCreateCustomer(UUID ownerId, CreateOrderRequest request, Wilaya wilaya) {
        return customers.findByOwnerIdAndPhone(ownerId, request.phone())
                .orElseGet(() -> customers.save(new Customer(ownerId,
                        request.customerName().trim(), request.phone(), wilaya,
                        request.commune().trim(), request.address())));
    }

    private BigDecimal resolveDeliveryFee(Order order) {
        DeliveryFee fee = fees
                .findByCarrierAndWilayaCode(order.getCarrier(), order.getWilaya().getCode())
                .orElseThrow(() -> new NotFoundException("Delivery fee",
                        order.getCarrier() + "/" + order.getWilaya().getCode()));

        return fee.priceFor(order.getDeliveryType());
    }

    /** Human-readable and sortable: WST-260901-0007. */
    private String nextOrderNumber(UUID ownerId) {
        long sequence = orders.countByOwner(ownerId) + 1;
        String today = LocalDate.now(ALGIERS).format(NUMBER_DATE);

        return "CMD-%s-%04d".formatted(today, sequence);
    }
}
