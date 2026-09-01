package com.westy.codmanager;

import com.westy.codmanager.common.exception.BusinessRuleException;
import com.westy.codmanager.order.domain.OrderStatus;
import com.westy.codmanager.order.state.OrderStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A plain unit test: no Spring context, no database, milliseconds to run.
 * The transition table is pure logic and deserves to be tested as such.
 */
class OrderStateMachineTest {

    private final OrderStateMachine machine = new OrderStateMachine();

    @Test
    void theHappyPathIsAllowedEndToEnd() {
        assertThat(machine.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED)).isTrue();
        assertThat(machine.canTransition(OrderStatus.CONFIRMED, OrderStatus.PACKED)).isTrue();
        assertThat(machine.canTransition(OrderStatus.PACKED, OrderStatus.SHIPPED)).isTrue();
        assertThat(machine.canTransition(OrderStatus.SHIPPED, OrderStatus.IN_TRANSIT)).isTrue();
        assertThat(machine.canTransition(OrderStatus.IN_TRANSIT, OrderStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(machine.canTransition(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED)).isTrue();
        assertThat(machine.canTransition(OrderStatus.DELIVERED, OrderStatus.SETTLED)).isTrue();
    }

    @Test
    void anOrderCannotSkipStraightToDelivered() {
        assertThat(machine.canTransition(OrderStatus.PENDING, OrderStatus.DELIVERED)).isFalse();

        assertThatThrownBy(() ->
                machine.assertTransition(OrderStatus.PENDING, OrderStatus.DELIVERED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void anUnansweredCallGoesBackIntoTheQueue() {
        assertThat(machine.canTransition(OrderStatus.PENDING, OrderStatus.NO_ANSWER)).isTrue();
        assertThat(machine.canTransition(OrderStatus.NO_ANSWER, OrderStatus.PENDING)).isTrue();
        assertThat(machine.canTransition(OrderStatus.NO_ANSWER, OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    void aFailedDeliveryAttemptReturnsToTransit() {
        assertThat(machine.canTransition(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.IN_TRANSIT)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SETTLED", "RETURNED", "CANCELLED"})
    void terminalStatesHaveNoWayOut(OrderStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(machine.nextStates(terminal)).isEmpty();
    }

    @Test
    void aShippedOrderCanNoLongerBeCancelled() {
        assertThat(machine.canTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void stockIsHeldOnlyFromConfirmedOnwards() {
        assertThat(OrderStatus.PENDING.holdsStock()).isFalse();
        assertThat(OrderStatus.NO_ANSWER.holdsStock()).isFalse();
        assertThat(OrderStatus.CONFIRMED.holdsStock()).isTrue();
        assertThat(OrderStatus.DELIVERED.holdsStock()).isTrue();
        assertThat(OrderStatus.RETURNED.holdsStock()).isFalse();
        assertThat(OrderStatus.CANCELLED.holdsStock()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void everyStatusIsDeclaredInTheTable(OrderStatus status) {
        assertThat(machine.nextStates(status)).isNotNull();
    }
}
