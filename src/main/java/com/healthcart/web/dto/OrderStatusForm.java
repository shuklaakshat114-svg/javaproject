package com.healthcart.web.dto;

import com.healthcart.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class OrderStatusForm {

    @NotNull(message = "Target status is required")
    private OrderStatus status;

    public OrderStatusForm() {
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
