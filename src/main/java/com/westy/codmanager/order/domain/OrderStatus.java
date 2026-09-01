package com.westy.codmanager.order.domain;

/**
 * The COD lifecycle.
 *
 * Two distinctions matter and are usually missed. NO_ANSWER is not a failure:
 * the customer simply did not pick up, and most of them answer on the second
 * or third try. And DELIVERED is not money: the cash sits with the carrier
 * until a remittance arrives, which is what SETTLED records.
 */
public enum OrderStatus {

    PENDING,
    NO_ANSWER,
    CONFIRMED,
    PACKED,
    SHIPPED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    SETTLED,
    RETURNED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SETTLED || this == RETURNED || this == CANCELLED;
    }

    /** From CONFIRMED onwards the stock is committed to this order. */
    public boolean holdsStock() {
        return switch (this) {
            case CONFIRMED, PACKED, SHIPPED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, SETTLED -> true;
            default -> false;
        };
    }
}
