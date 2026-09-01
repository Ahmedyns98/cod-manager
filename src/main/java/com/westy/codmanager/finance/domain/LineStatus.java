package com.westy.codmanager.finance.domain;

/**
 * Outcome of matching one payout row against an order.
 *
 * Only SETTLED moves money and advances the order. Everything else is recorded
 * for the seller to look at: quietly accepting a row that does not add up is
 * how discrepancies become invisible.
 */
public enum LineStatus {

    /** Matched an order, the amount agreed, the order moved to SETTLED. */
    SETTLED,

    /** Matched an order, but the carrier collected a different amount. */
    AMOUNT_MISMATCH,

    /** No shipment in this account carries that tracking number. */
    UNKNOWN_TRACKING,

    /** The order exists but never reached DELIVERED. */
    NOT_DELIVERED,

    /** Already settled by an earlier payout: a duplicate row. */
    ALREADY_SETTLED;

    public boolean needsAttention() {
        return this != SETTLED;
    }
}
