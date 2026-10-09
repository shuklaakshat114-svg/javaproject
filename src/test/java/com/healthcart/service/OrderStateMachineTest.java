package com.healthcart.service;

import com.healthcart.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order State Machine Lifecycle Tests")
class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    @Test
    @DisplayName("Valid forward lifecycle transitions succeed")
    void testValidForwardTransitions() {
        assertTrue(stateMachine.canTransition(OrderStatus.PLACED, OrderStatus.PAID));
        assertTrue(stateMachine.canTransition(OrderStatus.PAID, OrderStatus.CONFIRMED));
        assertTrue(stateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.PACKED));
        assertTrue(stateMachine.canTransition(OrderStatus.PACKED, OrderStatus.SHIPPED));
        assertTrue(stateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("Valid cancellation transitions succeed")
    void testValidCancellations() {
        assertTrue(stateMachine.canTransition(OrderStatus.PLACED, OrderStatus.CANCELLED));
        assertTrue(stateMachine.canTransition(OrderStatus.PAID, OrderStatus.CANCELLED));
        assertTrue(stateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("Payment failure from PLACED succeeds")
    void testPaymentFailureTransition() {
        assertTrue(stateMachine.canTransition(OrderStatus.PLACED, OrderStatus.PAYMENT_FAILED));
    }

    @ParameterizedTest
    @CsvSource({
            "DELIVERED, CANCELLED",
            "DELIVERED, SHIPPED",
            "CANCELLED, PAID",
            "CANCELLED, DELIVERED",
            "PAYMENT_FAILED, PAID",
            "PAYMENT_FAILED, DELIVERED",
            "PLACED, SHIPPED",
            "PLACED, DELIVERED",
            "CONFIRMED, DELIVERED",
            "SHIPPED, CANCELLED"
    })
    @DisplayName("Illegal state machine transitions fail validation")
    void testIllegalTransitions(OrderStatus current, OrderStatus target) {
        assertFalse(stateMachine.canTransition(current, target));
        assertThrows(IllegalStateException.class, () ->
                stateMachine.validateTransition(current, target, "HC-TEST-001"));
    }

    @Test
    @DisplayName("Terminal states have no outgoing transitions")
    void testTerminalStatesHaveNoAllowedNextStates() {
        assertTrue(stateMachine.getNextAllowedStates(OrderStatus.DELIVERED).isEmpty());
        assertTrue(stateMachine.getNextAllowedStates(OrderStatus.CANCELLED).isEmpty());
        assertTrue(stateMachine.getNextAllowedStates(OrderStatus.PAYMENT_FAILED).isEmpty());
    }

    @Test
    @DisplayName("Idempotent transition to same state is permitted")
    void testSameStateAllowed() {
        assertTrue(stateMachine.canTransition(OrderStatus.PAID, OrderStatus.PAID));
        assertDoesNotThrow(() -> stateMachine.validateTransition(OrderStatus.PAID, OrderStatus.PAID, "HC-TEST-001"));
    }
}
