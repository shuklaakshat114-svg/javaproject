package com.healthcart.service;

import com.healthcart.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class OrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachine.class);

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        // PLACED can transition to PAID, PAYMENT_FAILED, or CANCELLED
        VALID_TRANSITIONS.put(OrderStatus.PLACED, EnumSet.of(OrderStatus.PAID, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED));
        
        // PAID can transition to CONFIRMED or CANCELLED
        VALID_TRANSITIONS.put(OrderStatus.PAID, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));

        // CONFIRMED can transition to PACKED or CANCELLED
        VALID_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PACKED, OrderStatus.CANCELLED));

        // PACKED can transition to SHIPPED
        VALID_TRANSITIONS.put(OrderStatus.PACKED, EnumSet.of(OrderStatus.SHIPPED));

        // SHIPPED can transition to DELIVERED
        VALID_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));

        // Terminal states
        VALID_TRANSITIONS.put(OrderStatus.DELIVERED, Collections.emptySet());
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED, Collections.emptySet());
        VALID_TRANSITIONS.put(OrderStatus.PAYMENT_FAILED, Collections.emptySet());
    }

    public boolean canTransition(OrderStatus current, OrderStatus target) {
        if (current == null || target == null) {
            return false;
        }
        if (current == target) {
            return true;
        }
        Set<OrderStatus> allowed = VALID_TRANSITIONS.get(current);
        return allowed != null && allowed.contains(target);
    }

    public void validateTransition(OrderStatus current, OrderStatus target, String orderNumber) {
        if (!canTransition(current, target)) {
            String errorMsg = String.format("Illegal order state transition for order #%s: cannot move from %s to %s",
                    orderNumber, current, target);
            log.warn("ORDER STATE MACHINE VIOLATION: {}", errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    public Set<OrderStatus> getNextAllowedStates(OrderStatus current) {
        return VALID_TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }
}
