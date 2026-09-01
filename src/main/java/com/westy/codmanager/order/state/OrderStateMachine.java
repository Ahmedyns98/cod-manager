package com.westy.codmanager.order.state;

import com.westy.codmanager.common.exception.BusinessRuleException;
import com.westy.codmanager.order.domain.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.westy.codmanager.order.domain.OrderStatus.CANCELLED;
import static com.westy.codmanager.order.domain.OrderStatus.CONFIRMED;
import static com.westy.codmanager.order.domain.OrderStatus.DELIVERED;
import static com.westy.codmanager.order.domain.OrderStatus.IN_TRANSIT;
import static com.westy.codmanager.order.domain.OrderStatus.NO_ANSWER;
import static com.westy.codmanager.order.domain.OrderStatus.OUT_FOR_DELIVERY;
import static com.westy.codmanager.order.domain.OrderStatus.PACKED;
import static com.westy.codmanager.order.domain.OrderStatus.PENDING;
import static com.westy.codmanager.order.domain.OrderStatus.RETURNED;
import static com.westy.codmanager.order.domain.OrderStatus.SETTLED;
import static com.westy.codmanager.order.domain.OrderStatus.SHIPPED;

/**
 * The single place where a status change is allowed to happen.
 *
 * A plain transition table beats a state machine framework here: it is one
 * screen of code, it reads like the business rules it encodes, and it is
 * trivial to test exhaustively.
 */
@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED =
            new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING, Set.of(CONFIRMED, NO_ANSWER, CANCELLED));
        // Calling back is normal: NO_ANSWER returns to the queue.
        ALLOWED.put(NO_ANSWER, Set.of(PENDING, CONFIRMED, CANCELLED));
        ALLOWED.put(CONFIRMED, Set.of(PACKED, CANCELLED));
        ALLOWED.put(PACKED, Set.of(SHIPPED, CANCELLED));
        ALLOWED.put(SHIPPED, Set.of(IN_TRANSIT, RETURNED));
        ALLOWED.put(IN_TRANSIT, Set.of(OUT_FOR_DELIVERY, RETURNED));
        // A failed delivery attempt sends the parcel back into transit.
        ALLOWED.put(OUT_FOR_DELIVERY, Set.of(DELIVERED, IN_TRANSIT, RETURNED));
        ALLOWED.put(DELIVERED, Set.of(SETTLED));
        ALLOWED.put(SETTLED, Set.of());
        ALLOWED.put(RETURNED, Set.of());
        ALLOWED.put(CANCELLED, Set.of());
    }

    public boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public Set<OrderStatus> nextStates(OrderStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }

    public void assertTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessRuleException("ILLEGAL_TRANSITION",
                    "An order cannot go from %s to %s".formatted(from, to));
        }
    }
}
